#spring cloud 为开发人员提供了快速构建分布式系统的一些工具，包括配置管理、服务发现、断路器、路由、微代理、事件总线
#、全局锁、决策竞选、分布式会话等等。它运行环境简单，可以在开发人员的电脑上跑。
#另外说明spring cloud是基于springboot的，所以需要开发中对springboot有一定的了解

# Spring Boot版本2.0.3.RELEASE,Spring Cloud版本为Finchley.RELEASE

项目：-------springcloudbase-------------
一、服务的注册与发现Eureka(Finchley版本)
0. 服务注册中心：eureka-server
   服务提供者：  service-client

1. eureka.client.registerWithEureka：false
   fetchRegistry：false
   表明自己是一个eureka server.

2. eureka-server、service-client都需要配置注册到
   eureka.client.serviceUrl.defaultZone:http://${eureka.instance.hostname}:${server.port}/eureka/

3. 访问localhost:9090（即eureka-server）可查看注册的服务信息service-client
   http://localhost:9090/
   http://192.168.0.106:9091/hi

二、服务消费者（ribbon、Feign）(Finchley版本)--负载均衡客户端
    在微服务架构中，业务都会被拆分成一个独立的服务，服务与服务的通讯是基于http restful的。
    Spring cloud有两种服务调用方式，一种是ribbon+restTemplate，另一种是feign。
    ribbon是一个负载均衡客户端，可以很好的控制htt和tcp的一些行为。
    Feign默认集成了ribbon,采用的是基于接口的注解。

1. 一个服务注册中心，eureka-server,端口9090

2. service-client工程跑了两个实例，端口分别为9091,9092，分别向服务注册中心注册

3. sercvice-ribbon端口为9093,向服务注册中心注册

4. sercvice-ribbon中注入restTemplate，并通过@LoadBalanced注解表明这个restRemplate开启负载均衡的功能
   当sercvice-ribbon通过restTemplate调用service-hi的hi接口时
   因为用ribbon进行了负载均衡，会轮流的调用service-client：9091和9092 两个端口的hi接口
   http://localhost:9092/hi?name=test （访问多次，可看见，端口在变，实现了负载均衡）

5. service-feign中，接口中通过@FeignClient(value = "service-client")指明要访问的服务service-client
   在Controller层，直接调用接口
   http://localhost:9093/hi?name=test （访问多次，可看见，端口在变，实现了负载均衡）

三、断路器（Hystrix）(Finchley版本)

1. ribbon + Hystrix(熔断器)
     ribbon没有集成Hystrix，需引入jar包：spring-cloud-starter-netflix-hystrix
     在项目sercvice-ribbon的启动类ServiceRibbonApplication 加@EnableHystrix注解开启Hystrix
     在service层中的具体方法上加入：@HystrixCommand(fallbackMethod = "hiError")，fallbackMethod指明出错时执行
        的方法，编写方法名为hiError的方法。

2. feign + Hystrix
    feign是自带断路器的，直接在配置文件中打开即可：feign.hystrix.enabled=true
    在service-feign项目中，在接口的注解加入fallback，指定出错时交给那个类处理，但是该类需要实现该接口
    @FeignClient(value = "service-hi",fallback = SchedualServiceHiHystric.class)

3. 测试：启动eureka-server，service-client（可多个），service-feign/ribbon ,正常访问，然后关闭service-client，再访问。

四、路由网关(zuul)(Finchley版本)
    Zuul的主要功能是路由转发和过滤器
1. 路由
    service-zuul：  入口applicaton类加上注解@EnableZuulProxy开启zuul
    application.ym：以/api-a/ 开头的请求都转发给service-ribbon服务，
                    以/api-b/开头的请求都转发给service-feign服务
     http://localhost:9094/api-a/hi?name=forezp
     http://localhost:9094/api-b/hi?name=forezp

2. 过滤器
     MyFilter extends ZuulFilter ：这里做了一个安全验证，访问时需带上参数token
         filterType：返回一个字符串代表过滤器的类型，在zuul中定义了四种不同生命周期的过滤器类型，具体如下：
         pre：路由之前
         routing：路由之时
         post： 路由之后
         error：发送错误调用
         filterOrder：过滤的顺序
         shouldFilter：这里可以写逻辑判断，是否要过滤，本文true,永远过滤。
         run：过滤器的具体逻辑。可用很复杂，包括查sql，nosql去判断该请求到底有没有权限访问
      http://localhost:9094/api-a/hi?name=forezp&token=1

五、高可用的服务注册中心(Finchley版本)
     eureka-server 在不同的服务器上启动过个，但是各自之间相互注册

六、断路器监控(Hystrix Dashboard)(Finchley版本)
    在微服务架构中为例保证程序的可用性，防止程序出错导致网络阻塞，出现了断路器模型
    提供了数据监控和友好的图形化界面。

    项目：service-cli-dashboard-turbine
       在程序的入口ServiceHiApplication类，加上@EnableHystrix注解开启断路器，@EnableHystrixDashboard注解，开启HystrixDashboard
       程序中声明断路点@HystrixCommand
       前提:访问一遍程序中的接口，再看具体数据
       具体的数据： http://localhost:9096/actuator/hystrix.stream
       图形： http:localhost:9096/hystrix 填写http://localhost:9096/actuator/hystrix.stream等参数，看监控数据

七、断路器聚合监控(Hystrix Turbine)(Finchley版本)
    看整个系统的Hystrix Dashboard数据

1. service-turbine
     在其入口类ServiceTurbineApplication加上注解@EnableTurbine，开启turbine

2. service-cli-dashboard-turbine，修改application.name，和端口，开启多个实例

3. 配置文件中：appConfig: service-cli-dashboard-turbine,service-cli-dashboard-turbine2
              ### 配置Eureka中的serviceId列表，表明监控哪些服务

4.  最好先访问一遍监控服务中的接口，再看监控数据
    具体的数据： http://localhost:9095/turbine.stream
    图形： http://localhost:9095/hystrix 填写http://localhost:9095/turbine.stream等参数，看监控数据


项目：-------springcloudconfig-------------
八、分布式配置中心(Spring Cloud Config)(Finchley版本)
    方便服务配置文件统一管理，实时更新
    分布式配置中心组件spring cloud config
    支持配置服务放在配置服务的内存中（即本地），也支持放在远程Git仓库中
    分两个角色，一是config server，二是config client
	#注意填写远程仓库的用户名和密码
1. config-server，入口Application类加上@EnableConfigServer注解开启配置服务器的功能
    http://localhost:9098/foo/dev，看仓库文件
    注意：配置文件服务器部署之后，http请求访问配置是有固定格式的，最长用的:/{application}/{profile}[/{label}]，也就是说
    ，配置文件可能写的是:config-client-dev.yml或者是(properties), 那么可以访问
     : http://localhost:9098/config-client/dev 获取远程数据

    http请求地址和资源文件映射如下:
         /{application}/{profile}[/{label}]
         /{application}-{profile}.yml
         /{label}/{application}-{profile}.yml
         /{application}-{profile}.properties
         /{label}/{application}-{profile}.properties


2. config-client：其配置文件bootstrap.properties
      spring.cloud.config.label 指明远程仓库的分支
      spring.cloud.config.uri= http://localhost:9098/ 指明配置服务中心的网址。

3. config-client在配置文件中指向config-server，config-server在配置文件中指向git远程仓库文件，
   故config-client中的接口可以获取远程仓库中的数据
   http://localhost:9099/hi

九、高可用的分布式配置中心(Spring Cloud Config)(Finchley版本)
1. config-client-eureka与config-client基本相同
   config-server-eureka与config-server基本相同
   只是把config-client-eureka和config-server-eureka做成了服务注册到eurekaservercfig

2. eurekaservercfig服务注册中心

3. config-client-eureka对config-server-eureka的引用通过config-server-eureka的serviceid，而不是ip

4. 提供这样的命名的配置文件config-client-eureka-dev.profile
    http://localhost:9102/config-client-eureka/dev
    http://localhost:9101/hi

5. 在读取配置文件不再写ip地址，而是服务名，这时如果配置服务部署多份，通过负载均衡，从而高可用。()

十、消息总线(Spring Cloud Bus)(Finchley版本)
     Spring Cloud Bus 将分布式的节点用轻量的消息代理连接起来。它可以用于广播配置文件的更改或者服务之间的通讯
     ，也可以用于监控。本文要讲述的是用Spring Cloud Bus实现通知微服务架构的配置文件的更改
     按照官方文档，我们只需要在配置文件中配置 spring-cloud-starter-bus-amqp ；
      这就是说我们需要装rabbitMq

1. config-client-eureka加入：spring-cloud-starter-bus-amqp，spring-boot-starter-actuator
    在配置文件application.properties中加上RabbitMq的配置，
    包括RabbitMq的地址、端口，用户名、密码。以及spring.cloud.bus的配置
    启动类加入：@RefreshScope
2. 启动rabbitMq、config-server-eureka、config-client-eureka、eurekaservercfig

3. 访问config-client-eureka中的接口，http://localhost:9101/hi ，数据---foo version 3
   这时我们去代码仓库将foo的值，不需要重启服务，
   只需要发送post请求：http://localhost:9101/actuator/bus-refresh
   ，则config-client-eureka会重新读取配置文件

   另外，/actuator/bus-refresh接口可以指定服务，即使用"destination"参数，
   比如 “/actuator/bus-refresh?destination=customers:**” 即刷新服务名为customers的所有服务。


项目：-------spring-cloud-sleuth-------------
十一、服务链路追踪(Spring Cloud Sleuth)(Finchley版本)
    Spring Cloud Sleuth 主要功能就是在分布式系统中提供追踪解决方案，并且兼容支持了 zipkin
    ，只需要在pom文件中引入相应的依赖即可

    工程组成:一个server-zipkin,它的主要作用使用ZipkinServer 的功能，收集调用数据，并展示；
    一个service-hi,对外暴露hi接口；一个service-miya,对外暴露miya接口；这两个service可以相互调用；
    并且只有调用了，server-zipkin才会收集数据的，这就是为什么叫服务追踪了

1. server-zipkin
   在spring Cloud为F版本的时候，已经不需要自己构建Zipkin Server了，只需要下载jar即可
   下载：https://dl.bintray.com/openzipkin/maven/io/zipkin/java/zipkin-server/
   启动后访问浏览器：localhost:9411

2. service-first，service-second
      配置文件application.yml指定zipkin server的地址
      提供接口互相调用

3. 查看追踪
    http://localhost:9411/
    http://localhost:9103/hi或http://localhost:9104/miya
    再打开http://localhost:9411/的界面，点击Dependencies,可以发现服务的依赖关系
