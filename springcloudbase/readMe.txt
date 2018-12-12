#spring cloud 为开发人员提供了快速构建分布式系统的一些工具，包括配置管理、服务发现、断路器、路由、微代理、事件总线
#、全局锁、决策竞选、分布式会话等等。它运行环境简单，可以在开发人员的电脑上跑。
#另外说明spring cloud是基于springboot的，所以需要开发中对springboot有一定的了解

# Spring Boot版本2.0.3.RELEASE,Spring Cloud版本为Finchley.RELEASE

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

