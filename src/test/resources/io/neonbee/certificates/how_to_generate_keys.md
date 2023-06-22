# Generate Certificates

## Create the Root CA private key and certificate signing request
`openssl req -new -newkey rsa:4096 -nodes -out ca.csr -keyout ca.key`

## Create a self-signed Root CA Certificate
`openssl x509 -trustout -signkey ca.key -days 36500 -req -in ca.csr -out ca.pem`

## Create a private key and certificate signing request for cluster nodes
`openssl req -new -newkey rsa:4096 -nodes -out node{nodeId}.csr -keyout node{nodeId}.key`

## Create a certificate and sign it with the the RootCA key
`openssl x509 -req -days 36500 -in node{nodeId}.csr -CA ca.pem -CAkey ca.key -set_serial {nodeId} -out node{nodeId}.cer`

## Create the Truststore
`openssl pkcs12 -export -out truststore.p12 -inkey ca.key -in ca.pem`

## Create the Keystore
`openssl pkcs12 -export -out keystore-{nodeId}.p12 -inkey node{nodeId}.key -in node{nodeId}.cer -certfile ca.pem`
