package com.qianfeng.smsplatform.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianfeng.smsplatform.common.model.Standard_Submit;
import com.qianfeng.smsplatform.search.service.SearchApi;
import com.qianfeng.smsplatform.search.util.SearchPojo;
import com.qianfeng.smsplatform.search.util.SearchUtil;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @author damon
 * @Classname SearchApi
 * @Date 2019/12/4 15:34
 * @Description TODO
 */
@Slf4j
@Service
public class SearchApiImpl implements SearchApi {

    @Autowired
    private ObjectMapper objectMapper;

    private Logger logger = LoggerFactory.getLogger(SearchApiImpl.class);
    @Value("${elasticsearch.index.name}")
    private String indexName;
    @Value("${elasticsearch.index.type}")
    private String typeName;

    @Autowired
    private RestHighLevelClient client;


    @Override
    public void createIndex() throws Exception {
        //判断是否已经存在该索引
        //完成创建
        if(!existIndex(indexName)){
            CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
            //测试使用，实际开发中，副分片应该有两--->三个，每个主分片应该是20G——40G，写多读少，所以分片更小效率更高。
            createIndexRequest.settings(Settings.builder().put("number_of_replicas", 1).put("number_of_shards", 5).build());
            SearchUtil.buildMapping(typeName,createIndexRequest);
            client.indices().create(createIndexRequest,RequestOptions.DEFAULT);
        }else{
            logger.info("创建失败，索引已存在");
        }

    }

    @Override
    public void deleteIndex(String indexName) throws Exception {
        if(existIndex(indexName)){
            DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
            AcknowledgedResponse delete =  client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
            logger.info("删除index的结果是：{}",objectMapper.writeValueAsString(delete));
        }else {
            logger.error("删除失败，不存在指定索引");
        }
    }

    @Override
    public void add(String json) throws Exception {
        IndexRequest indexRequest = new IndexRequest(indexName, typeName);
        indexRequest.source(json, XContentType.JSON);
        IndexResponse response = client.index(indexRequest, RequestOptions.DEFAULT);
        logger.info("插入数据结果是:{}", objectMapper.writeValueAsString(response));
    }

    @Override
    public List<Map> search(String params) throws Exception {
        List list = new ArrayList();
        //和查询数量一样,根据条件查询结果,但是我们可能会有分页,可能还有高亮
        //将用户传递的json转换为map
        SearchPojo value = objectMapper.readValue(params, SearchPojo.class);
        SearchSourceBuilder builder = SearchUtil.getSearchSourceBuilder(value);
        //设置开始位置和总行数
        builder.from(value.getStart());
        builder.size(value.getRows());
        //应该要设置高亮数据,理论上高亮的标签应该是用户传递的,当然我们也有默认的
        //如果传递了请求参数才设置高亮
        if (value.getKeyword() != null) {
            //高亮的前缀和后缀
            Object highlightpretag = value.getHighLightPreTag();
            Object highlightposttag = value.getHighLightPostTag();

            if (highlightposttag == null || "".equalsIgnoreCase(highlightposttag.toString().trim())) {
                highlightposttag = "</span>";
            }
            if (highlightpretag == null || "".equalsIgnoreCase(highlightpretag.toString().trim())) {
                highlightpretag = "<span color='green'>";
            }
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("requestContent");
            highlightBuilder.preTags(highlightpretag.toString());
            highlightBuilder.postTags(highlightposttag.toString());
            builder.highlighter(highlightBuilder);
        }


        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source(builder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        //处理结果
        //命中的数据
        SearchHits hits = response.getHits();
        //获取到数据数组
        SearchHit[] searchHits = hits.getHits();
        for (SearchHit searchHit : searchHits) {
            //以json的方式获取数据
            String source = searchHit.getSourceAsString();
            //这是原始的数据
            Map data = objectMapper.readValue(source, Map.class);
            //可能会有高亮数据,获取高亮相关的数据
            Map<String, HighlightField> highlightFields = searchHit.getHighlightFields();
            HighlightField field = highlightFields.get("requestContent");
            if (field != null) {
                //有高亮
                Text[] fragments = field.getFragments();
                if (fragments != null) {
                    //高亮内容
                    String hlcontent = fragments[0].string();
                    //替换原始数据为高亮数据
                    data.put("requestContent", hlcontent);
                }
            }
            list.add(data);
        }
        return list;
    }

    @Override
    public long getCount(String params) throws Exception {
        //将用户传递的json转换为map
        SearchPojo value = objectMapper.readValue(params, SearchPojo.class);
        SearchSourceBuilder builder = SearchUtil.getSearchSourceBuilder(value);
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source(builder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        return response.getHits().getTotalHits();
    }

    /**
     * 更新状态报告
     * @param submit
     * @throws Exception
     */
    @Override
    public void update(Standard_Submit submit) throws Exception {
        existIndex(indexName);
        String id = getId(submit.getMsgid());
        UpdateRequest updateRequest = new UpdateRequest(indexName, typeName, id);
        updateRequest.doc(JSON.toJSONString(submit),XContentType.JSON);
        UpdateResponse response = client.update(updateRequest,RequestOptions.DEFAULT);
        logger.debug("更新结果{}",response);
    }

    /**
     * 由于插入数据时未指定id， 所以通过msgId 获取数据的id
     * @param msg
     * @return
     * @throws IOException
     */
    public String getId(String msg) throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);
        //根据msgID 进行查询，获取命中ID
        TermQueryBuilder termQueryBuilder = QueryBuilders.termQuery("msgid",msg);
        SearchSourceBuilder builder = new SearchSourceBuilder();
        builder.query(termQueryBuilder);
        searchRequest.source(builder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        SearchHits hits = response.getHits();
        //获取命中数据的ID
        return hits.getAt(0).getId();
    }

    /**
     * 判断是否存在索引
     * @param indexName
     * @return
     * @throws IOException
     */
    public boolean existIndex(String indexName) throws IOException {
        GetIndexRequest getIndexRequest = new GetIndexRequest();
        getIndexRequest.indices(indexName);
        return client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
    }
}
