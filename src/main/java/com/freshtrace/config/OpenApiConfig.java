package com.freshtrace.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 文档配置（springdoc-openapi）。
 * <p>
 * 访问地址（context-path 为 /api）：{@code /api/swagger-ui/index.html}，
 * OpenAPI JSON 为 {@code /api/v3/api-docs}。全局声明 Bearer JWT 鉴权，
 * 登录后把 token 填入 Swagger UI 的 Authorize 即可调试受保护接口。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI freshTraceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("鲜迹 FreshTrace API")
                        .version("v1.1")
                        .description("生鲜农产品溯源交易平台后端接口文档"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    @Bean
    public GroupedOpenApi userApi() {
        return group("01-用户与地址", "/user/**");
    }

    @Bean
    public GroupedOpenApi farmerApi() {
        return group("02-果农", "/farmer/**");
    }

    @Bean
    public GroupedOpenApi productApi() {
        return group("03-商品", "/category/**", "/spu/**", "/product/**");
    }

    @Bean
    public GroupedOpenApi tradeApi() {
        return group("04-交易", "/cart/**", "/order/**", "/payment/**", "/sub-order/**", "/refund/**");
    }

    @Bean
    public GroupedOpenApi reviewApi() {
        return group("05-评价", "/review/**");
    }

    @Bean
    public GroupedOpenApi traceApi() {
        return group("06-溯源", "/trace/**");
    }

    @Bean
    public GroupedOpenApi communityApi() {
        return group("07-社区", "/post/**");
    }

    @Bean
    public GroupedOpenApi presaleApi() {
        return group("08-预售", "/presale/**");
    }

    @Bean
    public GroupedOpenApi imApi() {
        return group("09-即时通讯与通知", "/im/**", "/notification/**");
    }

    @Bean
    public GroupedOpenApi reportApi() {
        return group("10-举报", "/report/**");
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return group("11-管理后台", "/admin/**");
    }

    private GroupedOpenApi group(String name, String... paths) {
        return GroupedOpenApi.builder()
                .group(name)
                .pathsToMatch(paths)
                .build();
    }
}
