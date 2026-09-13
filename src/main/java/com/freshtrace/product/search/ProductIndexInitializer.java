package com.freshtrace.product.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化商品 ES 索引（不存在则创建，含 IK 中文分词 mapping）。
 * <p>
 * 索引已存在时不做任何变更（避免误改线上 mapping）；初始化失败只记日志、不让应用启动失败。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class ProductIndexInitializer implements ApplicationRunner {

    private static final String IK_MAX_WORD = "ik_max_word";

    private final ElasticsearchClient elasticsearchClient;

    @Value("${elasticsearch.index:freshtrace_product}")
    private String index;

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean exists = elasticsearchClient.indices().exists(e -> e.index(index)).value();
            if (exists) {
                log.info("ES product index already exists: {}", index);
                return;
            }
            elasticsearchClient.indices().create(c -> c
                    .index(index)
                    .settings(s -> s.numberOfReplicas("0"))
                    .mappings(m -> m
                            .properties("id", p -> p.long_(l -> l))
                            .properties("title", p -> p.text(t -> t.analyzer(IK_MAX_WORD).searchAnalyzer(IK_MAX_WORD)))
                            .properties("description", p -> p.text(t -> t.analyzer(IK_MAX_WORD).searchAnalyzer(IK_MAX_WORD)))
                            .properties("spuName", p -> p.text(t -> t.analyzer(IK_MAX_WORD).searchAnalyzer(IK_MAX_WORD)))
                            .properties("variety", p -> p.text(t -> t.analyzer(IK_MAX_WORD).searchAnalyzer(IK_MAX_WORD)))
                            .properties("origin", p -> p.text(t -> t.analyzer(IK_MAX_WORD).searchAnalyzer(IK_MAX_WORD)))
                            .properties("tags", p -> p.text(t -> t.analyzer(IK_MAX_WORD).searchAnalyzer(IK_MAX_WORD)))
                            .properties("categoryId", p -> p.long_(l -> l))
                            .properties("categoryName", p -> p.keyword(k -> k))
                            .properties("farmerId", p -> p.long_(l -> l))
                            .properties("farmerName", p -> p.keyword(k -> k))
                            .properties("orchardName", p -> p.text(t -> t.analyzer(IK_MAX_WORD).searchAnalyzer(IK_MAX_WORD)))
                            .properties("avgRating", p -> p.double_(d -> d))
                            .properties("price", p -> p.double_(d -> d))
                            .properties("stock", p -> p.integer(i -> i))
                            .properties("salesCount", p -> p.integer(i -> i))
                            .properties("lifecycle", p -> p.integer(i -> i))
                            .properties("auditStatus", p -> p.integer(i -> i))
                            .properties("mainImage", p -> p.keyword(k -> k.index(false)))
                            .properties("unit", p -> p.keyword(k -> k.index(false)))
                            .properties("createTime", p -> p.long_(l -> l))));
            log.info("ES product index created: {}", index);
        } catch (Exception e) {
            log.error("ES product index init failed, index={}", index, e);
        }
    }
}
