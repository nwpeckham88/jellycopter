import sys
import os

# Ensure test/e2e is on sys.path so 'harness.*' imports cleanly without colliding with Python stdlib 'test'
E2E_DIR = os.path.dirname(os.path.abspath(__file__))
if E2E_DIR not in sys.path:
    sys.path.insert(0, E2E_DIR)
