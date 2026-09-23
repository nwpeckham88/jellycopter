"""
Jellycopter Audio DSP Subsystem Harness
Authoritative implementation of ITU-R BS.775 downmixer, dual-envelope DRC,
and 2.5kHz vocal formant peaking EQ adhering to ORIGINAL_REQUEST.md & Survey Analysis.
"""

import math
from typing import List, Tuple, Optional


class IturBs775Downmixer:
    """
    ITU-R BS.775 Multichannel to Stereo Downmixer with Center Dialogue Boost
    and Dynamic Anti-Clipping Normalization.
    """

    def __init__(self, center_boost_db: float = 3.0, enable_normalization: bool = True):
        # Clamped to [0.0, 6.0] dB
        self.center_boost_db = max(0.0, min(6.0, center_boost_db))
        self.enable_normalization = enable_normalization
        self._update_coefficients()

    def set_center_boost(self, boost_db: float):
        self.center_boost_db = max(0.0, min(6.0, boost_db))
        self._update_coefficients()

    def _update_coefficients(self):
        # Standard ITU-R center weighting is -3dB (1/sqrt(2) approx 0.70710678)
        # At beta=0dB: 1/sqrt(2) = 0.7071
        # At beta=3dB: 1.0000 (0dB)
        # At beta=6dB: sqrt(2) = 1.4142 (+3dB)
        self.alpha_c = math.pow(2.0, (self.center_boost_db - 3.0) / 6.0)
        # Surround weighting: 1 / sqrt(2) approx 0.70710678
        self.alpha_s = 1.0 / math.sqrt(2.0)

        if self.enable_normalization:
            # Dynamic normalization factor for 5.1: N = 1 / (1 + alpha_c + alpha_s)
            self.norm_factor = 1.0 / (1.0 + self.alpha_c + self.alpha_s)
            # Dynamic normalization factor for 7.1: N = 1 / (2.0 + alpha_c) matching Kotlin ItuRBs775Downmixer.kt
            self.norm_factor_7_1 = 1.0 / (2.0 + self.alpha_c)
        else:
            self.norm_factor = 1.0
            self.norm_factor_7_1 = 1.0

    def downmix_5_1(self, l: float, r: float, c: float, lfe: float, ls: float, rs: float) -> Tuple[float, float]:
        """
        Downmixes 5.1 multichannel audio sample (L, R, C, LFE, Ls, Rs) to Stereo (L_out, R_out).
        LFE is omitted per ITU-R BS.775 standard.
        """
        l_mix = l + (self.alpha_c * c) + (self.alpha_s * ls)
        r_mix = r + (self.alpha_c * c) + (self.alpha_s * rs)

        l_out = self.norm_factor * l_mix
        r_out = self.norm_factor * r_mix
        return (l_out, r_out)

    def downmix_7_1(
        self, l: float, r: float, c: float, lfe: float, ls: float, rs: float, rls: float, rrs: float
    ) -> Tuple[float, float]:
        """Downmixes 7.1 audio sample with effective surround summation and 7.1 normalization."""
        eff_ls = self.alpha_s * (ls + rls)
        eff_rs = self.alpha_s * (rs + rrs)
        l_mix = l + (self.alpha_c * c) + (self.alpha_s * eff_ls)
        r_mix = r + (self.alpha_c * c) + (self.alpha_s * eff_rs)
        l_out = self.norm_factor_7_1 * l_mix
        r_out = self.norm_factor_7_1 * r_mix
        return (l_out, r_out)

    def process_buffer(self, channels: List[List[float]]) -> List[List[float]]:
        """Processes multichannel buffer. Channels: [L, R] or [L, R, C, LFE, Ls, Rs]."""
        num_channels = len(channels)
        if num_channels <= 2:
            # Bypass (zero-cost passthrough)
            return channels

        num_samples = len(channels[0])
        out_l = [0.0] * num_samples
        out_r = [0.0] * num_samples

        if num_channels == 6:  # 5.1
            for i in range(num_samples):
                out_l[i], out_r[i] = self.downmix_5_1(
                    channels[0][i], channels[1][i], channels[2][i],
                    channels[3][i], channels[4][i], channels[5][i]
                )
        elif num_channels == 8:  # 7.1
            for i in range(num_samples):
                out_l[i], out_r[i] = self.downmix_7_1(
                    channels[0][i], channels[1][i], channels[2][i], channels[3][i],
                    channels[4][i], channels[5][i], channels[6][i], channels[7][i]
                )
        return [out_l, out_r]


class DynamicRangeCompressor:
    """
    Dual-Envelope Audio Night Mode Dynamic Range Compressor (DRC).
    Features:
    - Dialogue floor boost (+6 dB for signals < -40 dBFS)
    - Linear transition zone (-40 dBFS to -18 dBFS)
    - Compression ratio 4:1 above -18 dBFS
    - Clamping ceiling at -3 dBFS (transient peak suppression up to -12 dB)
    - Attack time: 10 ms, Release time: 300 ms
    """

    def __init__(
        self,
        sample_rate: int = 48000,
        floor_boost_db: float = 6.0,
        low_threshold_db: float = -40.0,
        high_threshold_db: float = -18.0,
        compression_ratio: float = 4.0,
        ceiling_db: float = -3.0,
        attack_ms: float = 10.0,
        release_ms: float = 300.0
    ):
        self.sample_rate = sample_rate
        self.floor_boost_db = floor_boost_db
        self.low_threshold_db = low_threshold_db
        self.high_threshold_db = high_threshold_db
        self.compression_ratio = compression_ratio
        self.ceiling_db = ceiling_db

        # Envelope smoothing coefficients
        self.alpha_attack = math.exp(-1.0 / ((attack_ms / 1000.0) * sample_rate))
        self.alpha_release = math.exp(-1.0 / ((release_ms / 1000.0) * sample_rate))
        self.envelope = 0.0

    def compute_static_gain_db(self, in_db: float) -> float:
        """Computes static transfer function gain G(x) in dB for input level x dBFS."""
        if in_db < self.low_threshold_db:
            return self.floor_boost_db
        elif in_db <= self.high_threshold_db:
            fraction = (self.high_threshold_db - in_db) / (self.high_threshold_db - self.low_threshold_db)
            return self.floor_boost_db * fraction
        else:
            # Compression above threshold
            compressed_target = self.high_threshold_db + ((in_db - self.high_threshold_db) / self.compression_ratio)
            comp_gain = compressed_target - in_db
            # Ceiling clamping
            if in_db + comp_gain > self.ceiling_db:
                comp_gain = self.ceiling_db - in_db
            return comp_gain

    def process_sample(self, sample: float) -> float:
        abs_sample = abs(sample)
        if abs_sample > self.envelope:
            self.envelope = self.alpha_attack * self.envelope + (1.0 - self.alpha_attack) * abs_sample
        else:
            self.envelope = self.alpha_release * self.envelope + (1.0 - self.alpha_release) * abs_sample

        if self.envelope < 1e-6:
            # Silent guard: apply floor boost
            gain_linear = math.pow(10.0, self.floor_boost_db / 20.0)
            return sample * gain_linear

        env_db = 20.0 * math.log10(self.envelope)
        gain_db = self.compute_static_gain_db(env_db)
        gain_linear = math.pow(10.0, gain_db / 20.0)
        return sample * gain_linear

    def process_buffer(self, samples: List[float]) -> List[float]:
        return [self.process_sample(s) for s in samples]


class VocalFormantPeakingFilter:
    """
    2nd-Order Biquad Parametric Peaking Filter targeting speech clarity.
    Parameters: f0 = 2500 Hz, Q = 1.0, Gain = +3.5 dB (Robert Bristow-Johnson EQ Cookbook).
    """

    def __init__(self, sample_rate: int = 48000, f0: float = 2500.0, q: float = 1.0, gain_db: float = 3.5):
        self.sample_rate = sample_rate
        self.f0 = f0
        self.q = q
        self.gain_db = gain_db
        self._calculate_coefficients()
        self.x1 = 0.0
        self.x2 = 0.0
        self.y1 = 0.0
        self.y2 = 0.0

    def _calculate_coefficients(self):
        w0 = 2.0 * math.pi * self.f0 / self.sample_rate
        alpha = math.sin(w0) / (2.0 * self.q)
        a = math.pow(10.0, self.gain_db / 40.0)

        b0 = 1.0 + alpha * a
        b1 = -2.0 * math.cos(w0)
        b2 = 1.0 - alpha * a
        a0 = 1.0 + alpha / a
        a1 = -2.0 * math.cos(w0)
        a2 = 1.0 - alpha / a

        # Normalize by a0
        self.b0 = b0 / a0
        self.b1 = b1 / a0
        self.b2 = b2 / a0
        self.a1 = a1 / a0
        self.a2 = a2 / a0

    def frequency_response_db(self, f: float) -> float:
        """Calculates magnitude response |H(e^{jw})| in dB at frequency f."""
        w = 2.0 * math.pi * f / self.sample_rate
        cos_w = math.cos(w)
        sin_w = math.sin(w)
        cos_2w = math.cos(2.0 * w)
        sin_2w = math.sin(2.0 * w)

        # Numerator: b0 + b1 e^{-jw} + b2 e^{-j2w}
        num_re = self.b0 + self.b1 * cos_w + self.b2 * cos_2w
        num_im = -self.b1 * sin_w - self.b2 * sin_2w

        # Denominator: 1 + a1 e^{-jw} + a2 e^{-j2w}
        den_re = 1.0 + self.a1 * cos_w + self.a2 * cos_2w
        den_im = -self.a1 * sin_w - self.a2 * sin_2w

        mag_num = math.sqrt(num_re * num_re + num_im * num_im)
        mag_den = math.sqrt(den_re * den_re + den_im * den_im)
        return 20.0 * math.log10(mag_num / mag_den)

    def process_sample(self, x: float) -> float:
        y = self.b0 * x + self.b1 * self.x1 + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2
        self.x2 = self.x1
        self.x1 = x
        self.y2 = self.y1
        self.y1 = y
        return y

    def process_buffer(self, samples: List[float]) -> List[float]:
        return [self.process_sample(s) for s in samples]
