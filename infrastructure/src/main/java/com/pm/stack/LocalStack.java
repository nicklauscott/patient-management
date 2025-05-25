package com.pm.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Stack from 'software.amazon.awscdk.Stack' package
public class LocalStack extends Stack {

    private final Vpc vpc;
    private final Cluster ecsCluster;

    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props); // initialize the 'Stack' with the necessary values
        this.vpc = createVpc();

        /// We will create both our database
        DatabaseInstance authServiceDb = createDatabase("AuthServiceDb", "auth-service-db");
        DatabaseInstance patientServiceDb = createDatabase("PatientServiceDb", "patient-service-db");

        /// We create health checks for our database
        CfnHealthCheck authDbHealthCheck = createDbHealthCheck(authServiceDb, "AuthServiceDbHealthCheck");
        CfnHealthCheck patientDbHealthCheck = createDbHealthCheck(patientServiceDb, "PatientServiceDbHealthCheck");

        /// We create our kafka cluster
        CfnCluster mskCluster = createMskCluster();

        /// We now create an ECS cluster to control all our ESC services
        this.ecsCluster = createEcsCluster();

        /// We now create our ECS Services; This is used to manage the ECS task like load balancers, managing the
        /// scaling, managing the number of resource a task needs, manging and restarting a failed task, etc.
        /// a 'Task' is what actually runs our container

        ///  AuthService
        FargateService authService =
                createFargateService(
                        "AuthService", "auth-service", /// Our Auth service image name
                        List.of(4005), authServiceDb,
                        Map.of("JWT_SECRET", "QWtVajVpZjUxcTMzcTJscU9aOFpqT3ZLMVVDTXRndEo=")
                );
        /// We tell the CDK that AuthService has dependency on 'authDbHealthCheck' and 'authServiceDb' so it needs
        /// to start both services before starting the AuthService
        authService.getNode().addDependency(authDbHealthCheck);
        authService.getNode().addDependency(authServiceDb);

        ///  BillingService
        FargateService billingService =
                createFargateService(
                        "BillingService", "billing-service", List.of(4001, 9001), null, null
                );

        ///  AnalyticsService
        FargateService analyticsService =
                createFargateService(
                        "AnalyticsService", "analytics-service", List.of(4002), null, null
                );
        /// AnalyticsService depends on Kafka
        analyticsService.getNode().addDependency(mskCluster);

        ///  PatientService
        FargateService patientService =
                createFargateService(
                        "PatientService", "patient-service",
                        List.of(4000), patientServiceDb,
                        /// LocalStack doesn't implement ECS cloud discovery functionality we can define the billing
                        /// service address as being on docker internal address and also the port
                        Map.of(
                                "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                                "BILLING_SERVICE_GRPC_PORT", "9001"
                        )
                );
        /// We tell the CDK that PatientService has dependency on the below services
        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(patientDbHealthCheck);
        patientService.getNode().addDependency(billingService);
        patientService.getNode().addDependency(mskCluster);

        /// We now create our ApiGatewayService with load balancer included
        createApiGateWayService();

        /// End of constructor
    }

    /// This creates the VPC container for our services
    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVpc")
                .vpcName("PatientManagementVpc")
                .maxAzs(2)
                .build();
    }

    /// This will be used to create the database we'll need in our container
    private DatabaseInstance createDatabase(String id, String dbName) {
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()))
                .vpc(vpc) /// We attach the database to our vpc container
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret( "admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY) /// 'RemovalPolicy.DESTROY' for testing
                .build();
    }

    /// This will create a health check for our db to provide our db status
    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id) {
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();
    }

    /// CfnCluster from 'software.amazon.awscdk.services.msk' which is a Kafka cluster package
    private CfnCluster createMskCluster() {
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafka-cluster")
                .kafkaVersion("2.8.0")
                /// The value must be 4 because '.maxAzs(2) [i.e, the availability zone]' in our 'createVpc()' method
                /// is 2 so 2 * 2 = 4
                .numberOfBrokerNodes(4)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.xlarge")
                        /// Because we can have multiple broker nodes, we want to make sure all
                        /// the nodes can access all other nodes in the vpc
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .map(ISubnet::getSubnetId)
                                .collect(Collectors.toList()))
                        /// This specifies how our brokers get distributed across our availability zones
                        .brokerAzDistribution("DEFAULT")
                        .build())
                .build();
    }

    /// We now an ECS cluster
    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCLuster")
                .vpc(vpc)
                /// This sets up a name space called "patient-management.local" so that our microservices
                /// can find and communicate with each other using this domain; this means an Auth Service
                /// running in our vpc container can be access like 'auth-service.patient-management.local'
                /// no ip address needed
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .build();
    }

    ///  This creates an ECS task
    private FargateService createFargateService(
        String id, String imageName, List<Integer> ports, DatabaseInstance db, Map<String, String> additionalEnvVars
    ) {
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, id + "Task")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        ///  The container we want the task to start
        ContainerDefinitionOptions.Builder containerOptions =
                ContainerDefinitionOptions.builder()
                        /// In production, the image will be pull from amazon register but with LocalStack
                        /// it will be pulled from our local Docker context
                        .image(ContainerImage.fromRegistry(imageName))
                        .portMappings(ports.stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port) /// This is the port the application is running on inside the container
                                        .hostPort(port) /// The port we want the container to expose so other services can access it
                                        .protocol(Protocol.TCP)
                                        .build()
                                ).toList())
                        /// We group all the log from the container into one log group, so it can be easy to find
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build()
                                )
                                .streamPrefix(imageName) /// This will prefix the image name to the logs
                                .build())
                        );

        Map<String, String> envVars = new HashMap<>();
        /// These are the three addresses that LocalStack could set up Kafka
        envVars.put(
                "SPRING_KAFKA_BOOTSTRAP_SERVERS",
                "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511, localhost.localstack.cloud:4512"
        );

        /// Configure services that are not null
        if (additionalEnvVars != null) envVars.putAll(additionalEnvVars);
        if (db != null) { /// '%s:%s/%s' placeholders for db address, port and imageName
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted(
                    db.getDbInstanceEndpointPort(),
                    db.getDbInstanceEndpointAddress(),
                    imageName
            ));
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            envVars.put(
                    "SPRING_DATASOURCE_PASSWORD",
                    /// The password will come from the database variable, whenever we create a database, the cdk
                    /// will create a password behind the scene and add it to the 'SecretManager'.
                    db.getSecret().secretValueFromJson("password").toString()
            );
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        /// we add the environment variable to the container
        containerOptions.environment(envVars);
        /// We then add our container to the task with the 'containerOptions'
        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();
    }

    private void createApiGateWayService() {
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, "APIGatewayTaskDefinition")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        ContainerDefinitionOptions containerOptions =
            ContainerDefinitionOptions.builder()
              .image(ContainerImage.fromRegistry("api-gateway"))
                  /// Because LocalStack doesn't implement ECS cloud discovery functionality, we manually provide the host and port
                    .environment(Map.of(
                            "SPRING_PROFILES_ACTIVE", "prod",
                            "AUTH_SERVICE_URL", "http://host.docker.internal:4005"
                    ))
              .portMappings(List.of(4004).stream()
                   .map(port -> PortMapping.builder().containerPort(port).hostPort(port).protocol(Protocol.TCP).build()).toList()
              )
              .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                  .logGroup(LogGroup.Builder.create(this,  "ApiGatewayLogGroup")
                       .logGroupName("/ecs/" + "api-gateway").removalPolicy(RemovalPolicy.DESTROY).retention(RetentionDays.ONE_DAY).build()
                  ).streamPrefix("api-gateway").build())
              ).build();

        taskDefinition.addContainer("APIGatewayServiceContainer", containerOptions);

        /// ApplicationLoadBalancedFargateService will create an application load balancer for us
        ApplicationLoadBalancedFargateService apiGateway = ApplicationLoadBalancedFargateService.Builder
                .create(this, "APIGatewayService")
                .cluster(ecsCluster)
                .serviceName("api-gateway")
                .taskDefinition(taskDefinition)
                .desiredCount(1)
                .healthCheckGracePeriod(Duration.seconds(60))
                .build();
    }

    public static void main(String[] args) {
        /// 'outdir("./cdk.out")'method is used to specify where the output will be
        App app = new App(AppProps.builder().outdir("./infrastructure/cdk.out").build());
        StackProps stackProps = StackProps.builder()
                /// 'synthesizer' method will be used to convert our code into a cloud formation template
                .synthesizer(new BootstraplessSynthesizer()).build();

        /// We then link the 'LocalStack' to the 'App' so the cdk will build our stack whenever we run the app.
        new LocalStack(app, "localstack", stackProps);
        app.synth();
        System.out.println("App synthesizing in progress...");
    }
}
