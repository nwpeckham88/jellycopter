"""
Jellycopter Playback & Video Engine Harness
Authoritative implementation of Dolby Vision Profile 7->8.1 rewriter,
HDR10 clean fallback, dual-engine fallback, and Smart Passthrough Handoff.
"""

from dataclasses import dataclass
from typing import Optional, Tuple
from datetime import time as dtime


@dataclass
class DeviceCapabilities:
    device_name: str
    is_dv_supported: bool
    is_dv_profile7_supported: bool
    is_dv_profile8_supported: bool
    is_hdr10_supported: bool
    supports_bitstream_passthrough: bool = True


@dataclass
class VideoFormat:
    sample_mime_type: str
    codecs: str
    dv_profile: Optional[int] = None
    dv_bl_signal_compatibility_id: Optional[int] = None
    color_space: str = "BT709"
    color_transfer: str = "SDR"


@dataclass
class PlaybackState:
    active_engine: str  # "MEDIA3" or "LIBMPV"
    position_ms: int
    audio_mode: str  # "BITSTREAM_PASSTHROUGH" or "DECODED_PCM_DSP"
    night_mode_active: bool
    force_stereo_active: bool
    subtitle_dimmer_active: bool
    subtitle_color_hex: str


class DolbyVisionTrackSelector:
    """
    Handles Dolby Vision Profile 7 (MEL/FEL) MKVs by rewriting metadata to Profile 8.1
    or cleanly falling back to HDR10 Base Layer, eliminating green/purple tint.
    """

    @staticmethod
    def resolve_track(input_format: VideoFormat, caps: DeviceCapabilities) -> VideoFormat:
        is_dv_profile7 = (
            input_format.dv_profile == 7 or
            "dvh1.07" in input_format.codecs or
            "dvhe.07" in input_format.codecs
        )

        if not is_dv_profile7:
            return input_format

        # Case 1: Device supports Dolby Vision Profile 8 (e.g. Shield TV, Onn 4K Pro)
        if caps.is_dv_supported and caps.is_dv_profile8_supported:
            return VideoFormat(
                sample_mime_type="video/dolby-vision",
                codecs="dvh1.08.06",
                dv_profile=8,
                dv_bl_signal_compatibility_id=1,  # Standard HDR10 base layer
                color_space="BT2020",
                color_transfer="SMPTE_ST_2084_PQ"
            )

        # Case 2: Display supports HDR10 but not Dolby Vision (or Profile 8 is unsupported)
        if caps.is_hdr10_supported:
            return VideoFormat(
                sample_mime_type="video/hevc",
                codecs="hvc1.2.4.L153.B0",
                dv_profile=None,
                dv_bl_signal_compatibility_id=None,
                color_space="BT2020",
                color_transfer="SMPTE_ST_2084_PQ"
            )

        # Case 3: SDR display fallback
        return VideoFormat(
            sample_mime_type="video/hevc",
            codecs="hvc1.1.6.L120.90",
            dv_profile=None,
            dv_bl_signal_compatibility_id=None,
            color_space="BT709",
            color_transfer="SDR"
        )


class PlayerEngineController:
    """
    Dual Playback Engine Controller:
    Coordinates Media3 (ExoPlayer) with automatic seamless fallback to libmpv,
    and manages Smart Passthrough Handoff.
    """

    def __init__(self, device_caps: DeviceCapabilities):
        self.caps = device_caps
        self.active_engine = "MEDIA3"
        self.position_ms = 0
        self.night_mode_enabled = False
        self.force_stereo_enabled = False
        self.subtitle_dimmer_enabled = False
        self.consecutive_dropped_frames = 0
        self.fallback_occurred = False
        self.fallback_reason = ""

    def update_position(self, pos_ms: int):
        self.position_ms = pos_ms

    def record_dropped_frames(self, count: int):
        self.consecutive_dropped_frames += count
        if self.consecutive_dropped_frames > 30 and self.active_engine == "MEDIA3":
            self.trigger_fallback("EXCESSIVE_FRAME_DROPS_SURPASSED_30")

    def handle_player_error(self, error_code: str):
        if self.active_engine == "MEDIA3":
            if error_code in ("ERROR_CODE_DECODER_INIT_FAILED", "ERROR_CODE_DECODER_QUERY_FAILED"):
                self.trigger_fallback(error_code)

    def trigger_fallback(self, reason: str):
        self.active_engine = "LIBMPV"
        self.fallback_occurred = True
        self.fallback_reason = reason

    def set_night_mode(self, enabled: bool):
        self.night_mode_enabled = enabled
        self.subtitle_dimmer_enabled = enabled

    def set_force_stereo(self, enabled: bool):
        self.force_stereo_enabled = enabled

    def get_audio_mode(self) -> str:
        """
        Smart Passthrough Handoff:
        Bitstream passthrough during normal playback;
        hands off to Decoded PCM DSP when Night Mode or Force Stereo is active.
        """
        if not self.caps.supports_bitstream_passthrough:
            return "DECODED_PCM_DSP"
        if self.night_mode_enabled or self.force_stereo_enabled:
            return "DECODED_PCM_DSP"
        return "BITSTREAM_PASSTHROUGH"

    def get_subtitle_color(self) -> str:
        """50% luminance dimmed grey (#80B0B0B0) during Night Mode to prevent glare."""
        if self.subtitle_dimmer_enabled:
            return "#80B0B0B0"
        return "#FFFFFFFF"

    def get_current_state(self) -> PlaybackState:
        return PlaybackState(
            active_engine=self.active_engine,
            position_ms=self.position_ms,
            audio_mode=self.get_audio_mode(),
            night_mode_active=self.night_mode_enabled,
            force_stereo_active=self.force_stereo_enabled,
            subtitle_dimmer_active=self.subtitle_dimmer_enabled,
            subtitle_color_hex=self.get_subtitle_color()
        )


class AutoNightModeScheduler:
    """Evaluates time-based quiet hours with support for crossing midnight."""

    def __init__(self, start_time: dtime, end_time: dtime, enabled: bool = True):
        self.start_time = start_time
        self.end_time = end_time
        self.enabled = enabled

    def is_night_time(self, current_time: dtime) -> bool:
        if not self.enabled:
            return False
        if self.start_time <= self.end_time:
            return self.start_time <= current_time <= self.end_time
        else:
            # Crosses midnight (e.g. 22:30 to 07:00)
            return current_time >= self.start_time or current_time < self.end_time
