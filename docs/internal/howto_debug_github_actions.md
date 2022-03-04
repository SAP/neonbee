## **How To:** Debug / SSH into the GitHub actions workflow

This document describes how to hook into the GitHub actions workflow if required using a remote SSH session.

Add the following workflow step into any position of the `.github/workflows` file of your choice, e.g. [`voter.yml`](../../.github/workflows/voter.yml):

```yaml
- name: Setup tmate session
  uses: mxschmitt/action-tmate@v3
```

It causes a [tmate](https://tmate.io/) session to be spun up (attention it is a public session, anybody could hook into it, when they monitor our build logs, protection would be possible with options of the `action-tmate`). The action *will wait* until you connect to the session via SSH and create a `continue` file (e.g. by using `touch continue`). Wait for the step to execute, checking the build logs you should see an SSH info like:

```bash
SSH: ssh A7BF8dKH8jHw7KnzBRAUGB42c@sfo2.tmate.io
```

This allows you to connect to connect to the container, before an action is executed, or afterwards, depending on the use case you are trying to debug. Connect to it via an SSH tool of your choice and hit `q` to close the welcome message. You are now on the machine, with step execution halted. in case you would like to continue the workflow create a file called `continue`:

```bash
touch continue
```

**Important**: Don't forget to **remove** the action again, once you merge your change. The `tmate` action should not stay in the workflow, when being merged to the main branch.

### Things you might be interested to do on the container

```bash
# grep all java processes running
pgrep -fl java
# or scan top in order to find out from a CPU point of view, which java process is most likely stuck
top
# use jstack to take a thread dump
jstack 3303 > /tmp/threaddump.txt
# use a service like https://transfer.sh/ to upload the dump
curl --upload-file /tmp/threaddump.txt https://transfer.sh/threaddump.txt
```