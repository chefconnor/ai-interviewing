# Python VAD Detector

A lightweight Voice Activity Detection (VAD) script using webrtcvad for integration with Java applications.

## Installation

```bash
pip install -r requirements.txt
```

## Usage

The script reads 320-byte chunks (20ms of 16kHz, 16-bit mono audio) from stdin and outputs:
- `1` if speech is detected
- `0` if no speech is detected

### Python Usage
```bash
python3 vad_check.py < audio_chunks.raw
```

### Java Integration
```java
ProcessBuilder builder = new ProcessBuilder("python3", "/path/to/vad_check.py");
Process vadProcess = builder.start();
```

## Requirements
- Python 3.6+
- webrtcvad 2.0.10
- Audio format: 16kHz, 16-bit mono PCM