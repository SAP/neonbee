# Docker Image

## Pull the image
To pull the image, be sure to be logged into ghcr.
Therefore create a token in [https://github.com/settings/tokens](https://github.com/settings/tokens) and select `read:packages`.

Now the token can be used to log into ghcr.

```console
docker login ghcr.io -u <username> -p <token>
```

Pull the latest (stable) image from the command line

```console
# latest
docker pull ghcr.io/sap/neonbee:latest

# via tag
docker pull ghcr.io/sap/neonbee:0.14.0

# nightly build
docker pull ghcr.io/sap/neonbee:main-f73e852
```

Use the NeonBee image as base image in Dockerfile:

```Dockerfile
FROM ghcr.io/sap/neonbee:latest
```

## Start NeonBee from a container

Start a container with the latest neonbee-core image and expose port 8080 (NeonBee default).

```sh
docker run --rm -it -p 8080:8080 ghcr.io/sap/neonbee:latest
```

Note: If you would like to specify a different container port you can do this by passing the container port either via env variable:

```sh
docker run --rm -it -p 8080:81 -e NEONBEE_PORT=81 ghcr.io/sap/neonbee:latest
```

or via command line option:

```sh
docker run --rm -it -p 8080:81 ghcr.io/sap/neonbee:latest -port 81
```

This will boot up neonbee and you should see a log message like

```console
INFO io.neonbee.NeonBee - Successfully booted NeonBee (ID: 6374fa53-8a08-471c-8271-9204e59f90e8})!
```

You can verify the installation by curl'ing the health endpoint

```sh
curl -s http://localhost:8080/health | jq -r .
```

Tip: In order to see a full list of available command line options, run

```sh
docker run --rm -it ghcr.io/sap/neonbee:latest --help
```

## Build Docker image

To build the docker image by your own, e.g. for development purposes on your local machine, you can run

```sh
docker build -t neonbee-core .
```

Note, if you run on a M1 chip you probably need to add a `--platform=linux/amd64` to the docker build command above.