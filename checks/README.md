# Regression checks

With Java 21 on PATH (or JAVA_HOME set), run `powershell -File checks/run.ps1` from the repository root.

The standalone harness checks station parsing, favorites migration and persistence,
filtering, player placement, overlapping search responses, retry states, stalled-stream
cancellation and minimum window width. It uses an isolated temporary user home under
target/verification, synthetic station responses and a local test socket.

JavaFX briefly opens a test window and writes an interface snapshot in the test folder.
It does not test audio output from a live station.
