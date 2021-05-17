# seckill
> a basic e-commerce seckill project built by Spring Boot for learning to optimize the server.

1. Use MVC architecture and domain-driven layered model;
2. Use *Spring Boot*, *Spring MVC*, *MyBatis*, *Nginx*, *Guava*, *Redis* and *RocketMQ*;
3. Use Nginx for **reverse proxy** and **distributed expansion** to handle static resources and Ajax requests respectively;
4. The **three-level** cache constructed by Redis, Guava and Nginx shared dic caches product information and improves the QPS of the product detail page greatly;
5. Use Redis to cache transaction verification and inventory information, and use **RocketMQ transactional messages** to perform asynchronous inventory deduction, which greatly increases the TPS of the order operation and also ensures the final consistency of inventory data;
6. Use the "**token + token gate + queue**" method to cut peaks, which greatly reduces the flow of the order interface; uses the verification code to smooth the flow, and allocates the peak from 1 second to 5 seconds; uses the token bucket algorithm to limit the flow, to limit the single-machine TPS, and to ensure the availability of services;
7. Deployed Nginx, Tomcat, MySQL, Redis and RocketMQ in CentOS 8 (2 core 4G) environment, and used JMeter for the stress test (3000 threads * 20 times), the result shows that product details function QPS can reach 5500+ and order operation TPS up to 6400+.

![](/pic1.png)

![](/pic2.png)