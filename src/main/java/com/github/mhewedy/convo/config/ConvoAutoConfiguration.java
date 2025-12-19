package com.github.mhewedy.convo.config;

import com.github.mhewedy.convo.AbstractConversationHolder;
import com.github.mhewedy.convo.ConversationFilter;
import com.github.mhewedy.convo.ConversationRepository;
import com.github.mhewedy.convo.IdGenerator;
import com.github.mhewedy.convo.store.JdbcStoreRepository;
import com.github.mhewedy.convo.store.RedisStoreRepository;
import com.github.mhewedy.convo.store.StoreRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableConfigurationProperties(ConvoProperties.class)
public class ConvoAutoConfiguration {

    @Bean
    public ConversationRepository conversationRepository(IdGenerator idGenerator,
                                                         ObjectMapper objectMapper,
                                                         StoreRepository storeRepository) {
        return new ConversationRepository(idGenerator, objectMapper, storeRepository);
    }

    @Bean
    public FilterRegistrationBean<ConversationFilter> conversationFilter() {
        FilterRegistrationBean<ConversationFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new ConversationFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);

        return registrationBean;
    }


    @Configuration
    @ConditionalOnClass(RedisTemplate.class)
    public static class RedisConfig {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(value = "convo.store", havingValue = "redis", matchIfMissing = true)
        public RedisTemplate<String, AbstractConversationHolder> redisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                                               ObjectMapper objectMapper) {
            RedisTemplate<String, AbstractConversationHolder> template = new RedisTemplate<>();
            template.setConnectionFactory(redisConnectionFactory);

            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new GenericJacksonJsonRedisSerializer(objectMapper));

            return template;
        }

        @Bean
        @Primary
        @ConditionalOnMissingBean
        @ConditionalOnProperty(value = "convo.store", havingValue = "redis", matchIfMissing = true)
        public RedisStoreRepository redisStoreRepository(RedisTemplate<String, AbstractConversationHolder> redisTemplate) {
            return new RedisStoreRepository(redisTemplate);
        }
    }

    @Configuration
    @ConditionalOnClass(NamedParameterJdbcTemplate.class)
    public static class JdbcConfig {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(value = "convo.store", havingValue = "jdbc", matchIfMissing = true)
        public JdbcStoreRepository jdbcStoreRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                                       ConvoProperties properties, ScheduledExecutorService cleanupExecutorService,
                                                       TransactionTemplate transactionTemplate) {
            return new JdbcStoreRepository(objectMapper, jdbcTemplate, properties, cleanupExecutorService, transactionTemplate);
        }
    }

    @Bean
    public ScheduledExecutorService cleanupExecutorService() {
        return Executors.newSingleThreadScheduledExecutor();
    }
}
