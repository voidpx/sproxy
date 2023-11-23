# A simple proxy

This project implements an encrypted & mutually authenticated (with public key authentication) tunnel 
where the client side provides SOCKS5(not fully compliant) proxy functionality, 
it's similar to the SOCKS proxy functionality provided by `ssh`, but it has better connection resilience at least in my use cases. 
it can also be used as a standalone SOCKS5 proxy in which case it provides no encryption/authentication.

### Build

execute

    mvn clean package
    
to build an executable jar.

### Usage

execute
    
    java -jar sproxy-1.0.0.jar -h
    
to get the help text:

    Usage:                                                                                                                                                               
    sub commands:                                                                                                                                                        
      client - run as a tunnel client proxy                                                                                                                              
        -h <host> - address at which the tunnel client will be listening                                                                                                 
        -p <port> - port on which the tunnel client will be listening                                                                                                    
        -H <host> - remote address at which the tunnel server is listening                                                                                               
        -P <port> - remote port on which the tunnel server is listening                                                                                                  
        -k <keystore file> - keystore file(pkcs12) that contains the key to be used by the tunnel client to authenticate with the tunnel server                          
      server - run as a tunnel server                                                                                                                                    
        -h <host> - address at which the tunnel server will be listening                                                                                                 
        -p <port> - port on which the tunnel server will be listening
        -6 if present, listen on ipv6 address                                                                                                    
        -a <authorized key file> - file that contains the public keys that are authorized to connect to this tunnel server                                               
      genKey - generate a key pair(RSA) for tunnel client and server respectively for mutual authentication, generated files are:                                        
        tc.p12 - tunnel client key store                                                                                                                                 
        tc_authorized_keys - this file contains public key of the tunnel server that the tunnel client trusts, more public keys can be added with the addAuthKey command 
        ts.p12 - tunnel server key store                                                                                                                                 
        ts_authorized_keys - this file contains public keys of the tunnel client that the tunnel server trusts, more public keys can be added with the addAuthKey command
      addAuthKey - add a public key to the list of authorized keys used by the tunnel server                                                                             
        -k <keystore file> - keystore file(pkcs12) from which to extract the public key                                                                                  
        -a <authorized key file> - file to store(append) the extracted public key                                                                                        
      standalone - run as a standalone proxy that provides no authentication or encryption                                                                               
        -h <host> - address at which the proxy will be listening                                                                                                         
        -p <port> - port on which the proxy will be listening                                                                                                            


### Deploy

1. execute

```bash
java -jar sproxy-1.0.0.jar genKey
```
to generate related key files.

2. copy `ts.p12` and `ts_authorized_keys` and the built jar file to the server where the tunnel server is to be running. execute

```bash
java -jar sproxy-1.0.0.jar server -h <listening host> -p <port>
```
to start the tunnel server.

3. copy `tc.p12` and `tc_authorized_keys` and the built jar file to a location where the tunnel client is to be running, typically it's the machine where you use the browser. execute

```
java -jar sproxy-1.0.0.jar client -h <tunnel client listening host> -p <port> -H <tunnel server host> -P <tunnel server port>
```
to start the tunnel client.

4. change the system proxy settings to point to the proxy host/port used in step 3. execute

```bash
curl --preproxy socks5://<tunnel client listening host>:<port> <http url to fetch>
```
to test if it works.

### libs used

* [Bouncy Castle](https://www.bouncycastle.org/)
* [Slf4j](https://www.slf4j.org/)
* [JUnit 5](https://junit.org/junit5/)
* [Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/docs/3.0.x/maven-plugin/reference/htmlsingle/)
* [Lombok](https://projectlombok.org/)
