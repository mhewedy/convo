package com.github.mhewedy.convo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mhewedy.convo.store.JdbcStoreRepository;
import com.github.mhewedy.convo.store.RedisStoreRepository;
import com.github.mhewedy.convo.store.StoreRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
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
        public RedisStoreRepository redisStoreRepository(RedisTemplate<String, AbstractConversationHolder> redisTemplate) {
            return new RedisStoreRepository(redisTemplate);
        }
    }

    @Configuration
    @ConditionalOnClass(NamedParameterJdbcTemplate.class)
    public static class JdbcConfig {

        @Bean
        @ConditionalOnMissingBean
        public JdbcStoreRepository jdbcStoreRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            return new JdbcStoreRepository(objectMapper, jdbcTemplate);
        }
    }
}
