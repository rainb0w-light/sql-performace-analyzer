package com.biz.sccba.sqlanalyzer.config;


import org.h2.tools.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;

@Configuration
public class H2Server {

    @Bean(initMethod = "start" ,destroyMethod = "stop")
    public Server H2TcpServer() throws SQLException {
        return Server.createTcpServer("-tcp","-tcpAllowOthers","-tcpPort","9092");
    }

}
