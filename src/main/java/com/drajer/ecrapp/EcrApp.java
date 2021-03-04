package com.drajer.ecrapp;

import com.drajer.ecrapp.security.AuthorizationService;
import com.drajer.ecrapp.security.SampleAuthorizationServiceImpl;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(exclude = HibernateJpaAutoConfiguration.class)
@EnableTransactionManagement
@ServletComponentScan
public class EcrApp extends SpringBootServletInitializer {

  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.sources(EcrApp.class);
  }

  public static void main(String[] args) {
    SpringApplication.run(EcrApp.class, args);
  }

  @Bean
  public RestTemplate restTemplate()
      throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

    SSLContext sslContext =
        org.apache.http.ssl.SSLContexts.custom()
            .loadTrustMaterial(null, acceptingTrustStrategy)
            .build();

    SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);

    CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(csf).build();

    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory();

    requestFactory.setHttpClient(httpClient);
    // RestTemplate restTemplate = new RestTemplate(requestFactory);
    return new RestTemplate(requestFactory);
  }

  @Bean
  public AuthorizationService authorizationService() {
    return new SampleAuthorizationServiceImpl();
  }
}
