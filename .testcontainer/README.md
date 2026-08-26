# Test Container

The test container runs the Maven verification suite, downloads the pinned Paper 26.2 build 92
server from PaperMC's official downloads service, and installs the resulting plugin jar. It is a
manual smoke-test environment; unit and protocol gates still run during the image build.

## Modifying ops.json

The `ops.json` file gives test players operator permissions. Add the test account's username and
UUID before building the image.
