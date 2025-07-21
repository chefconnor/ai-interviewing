#!/usr/bin/env python3
import sys
import os
import warnings

# Suppress pkg_resources deprecation warning
warnings.filterwarnings("ignore", category=UserWarning, module="webrtcvad")

import webrtcvad

# Ensure unbuffered output
sys.stdout = os.fdopen(sys.stdout.fileno(), 'w', 1)
sys.stderr = os.fdopen(sys.stderr.fileno(), 'w', 1)

try:
    vad = webrtcvad.Vad(2)
    print("VAD initialized", file=sys.stderr)
except Exception as e:
    print(f"Failed to initialize VAD: {e}", file=sys.stderr)
    sys.exit(1)

def is_speech(audio_bytes, sample_rate=16000):
    return vad.is_speech(audio_bytes, sample_rate)

def main():
    try:
        while True:
            chunk = sys.stdin.buffer.read(320)
            if not chunk:
                print("No data received, exiting", file=sys.stderr)
                break
            if len(chunk) < 320:
                print(f"Incomplete chunk: {len(chunk)} bytes", file=sys.stderr)
                break
            
            try:
                if is_speech(chunk):
                    sys.stdout.write("1\n")
                else:
                    sys.stdout.write("0\n")
                sys.stdout.flush()
            except Exception as e:
                print(f"VAD processing error: {e}", file=sys.stderr)
                sys.stdout.write("0\n")
                sys.stdout.flush()
                
    except KeyboardInterrupt:
        print("Interrupted by user", file=sys.stderr)
    except Exception as e:
        print(f"Unexpected error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc(file=sys.stderr)

if __name__ == "__main__":
    main()