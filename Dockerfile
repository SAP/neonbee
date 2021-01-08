# Use an the sapmachine runtime as a parent image
FROM sapmachine/jdk11

# The build NeonBee version
ARG NEONBEE_VERSION

# Set the working directory to /neonbee
WORKDIR /neonbee

# Copy the current directory contents into the container at /neonbee
ADD neonbee-dist-${NEONBEE_VERSION}.tar.gz /neonbee/

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Make NeonBee start script executable
RUN chmod +x start.sh

# Run start.sh when the container launches
CMD ["/neonbee/start.sh"]