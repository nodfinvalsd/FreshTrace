ARG ES_VERSION=8.13.4
FROM docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION}

# 在构建阶段重新声明 ARG，才能在 RUN 中使用
ARG ES_VERSION

# 安装 IK 中文分词插件（提供 ik_max_word / ik_smart 分析器）
RUN bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/${ES_VERSION}
