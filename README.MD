# FTHBTH
For talentless hacks by talentless hacks
### Purpose
This is a simple AI prompt generator that monitors one of the audio streams on the device, dispatches the audio data to Google Voice, and then sends those instructions to Cerebras or OpenAI as prompts.
Its purpose is to facilitate technical conversations in real time, so that the user seems smarter and more sophisticated than he really is.

## Legal Disclaimer
Additionally, as of 2025, the following U.S. states are considered two-party consent states (also called "all-party consent" states). This means that everyone involved in a conversation must give consent before the conversation can be legally recorded (in person or by phone).

Even if you are in a one-party consent state, it is still illegal to record conversations without the other party's knowledge and consent. Federal courts will side with the two-party consent state.
- California
- Connecticut
- Delaware*
- Florida
- Illinois
- Maryland
- Massachusetts
- Montana
- Nevada
- New Hampshire
- Pennsylvania
- Washington

## Technical details
It is set up to use Cerebras AI, because it is the fastest available. Grok would also be acceptable. OpenAI is not fast enough to be useful in conversation.

### Usage

You will need to set an environment variable for the API key.
CEREBRAS_API_KEY=UNQUOTED_KEY

If you want to add another service, or use cerebras, you will need your own API key.
(OPENAI_API_KEY=UNQUOTED_KEY)

To monitor the promts without seeing the logs, you can use the following bash command:

"""tail -f ai_output.log"""

On startup, you will be prompted to choose an audio channel to monitor.

At any point during operation, you can press 'i', ENTER. You will be prompted to add a persistent instruction to prepend to each prompt.

You can also press 'q' to exit gracefully.
