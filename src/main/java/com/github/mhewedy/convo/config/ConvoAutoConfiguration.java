package com.github.mhewedy.convo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
        @Primary
        @ConditionalOnMissingBean
        @ConditionalOnProperty(value = "convo.store", havingValue = "redis", matchIfMissing = true)
        public RedisStoreRepository redisStoreRepository(RedisTemplate<Object, Object> redisTemplate) {
            return new RedisStoreRepository(redisTemplate);
        }
    }

    @Configuration
    @ConditionalOnClass(NamedParameterJdbcTemplate.class)
    public static class JdbcConfig {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(value = "convo.store", havingValue = "jdbc", matchIfMissing = true)
        public JdbcStoreRepository jdbcStoreRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            return new JdbcStoreRepository(objectMapper, jdbcTemplate);
        }
    }
}
