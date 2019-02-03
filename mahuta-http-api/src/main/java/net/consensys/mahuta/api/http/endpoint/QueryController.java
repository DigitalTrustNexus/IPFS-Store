package net.consensys.mahuta.api.http.endpoint;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import net.consensys.mahuta.core.Mahuta;
import net.consensys.mahuta.core.domain.common.pagination.PageRequest;
import net.consensys.mahuta.core.domain.common.pagination.PageRequest.SortDirection;
import net.consensys.mahuta.core.domain.common.query.Query;
import net.consensys.mahuta.core.domain.get.GetResponse;
import net.consensys.mahuta.core.domain.search.SearchResponse;
import net.consensys.mahuta.core.exception.NotFoundException;
import net.consensys.mahuta.core.utils.lamba.Throwing;

@RestController
@Slf4j
public class QueryController {

    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final String DEFAULT_PAGE_NO = "0";

    private final ObjectMapper mapper;
    private final Mahuta mahuta;

    @Autowired
    public QueryController(Mahuta mahuta) {
        this.mahuta = mahuta;
        this.mapper = new ObjectMapper();
    }

    @GetMapping(value = "${mahuta.api-spec.v1.query.fetch}")
    public @ResponseBody ResponseEntity<byte[]> getFile(@PathVariable(value = "hash") @NotNull String hash,
            @RequestParam(value = "index", required = false) String indexName, HttpServletResponse response) {

        // Find and get content by hash
        GetResponse resp;
        try {
            resp = mahuta.prepareGet().indexName(indexName).contentId(hash).loadFile(true).execute();
        } catch (NotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
        
        // Attach content-type to the header
        response.setContentType(Optional.ofNullable(resp.getMetadata().getContentType())
                .orElseGet(() -> "application/octet-stream"));
        log.info("response.getContentType()={}", response.getContentType());

        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(Optional.ofNullable(resp.getMetadata().getContentType())
                .map(MediaType::valueOf).orElseGet(() -> MediaType.APPLICATION_OCTET_STREAM));

        return new ResponseEntity<>(
                ((ByteArrayOutputStream) resp.getPayload()).toByteArray(),
                httpHeaders, 
                HttpStatus.OK);
    }

    @PostMapping(value = "${mahuta.api-spec.v1.query.search}", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody SearchResponse searchContentsByPost(
            @RequestParam(value = "index", required = false) String indexName,
            @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE_NO) int pageNo,
            @RequestParam(value = "size", required = false, defaultValue = DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(value = "sort", required = false) Optional<String> sortAttribute,
            @RequestParam(value = "dir", required = false, defaultValue = "ASC") SortDirection sortDirection,
            @RequestBody Query query) {

        return executeSearch(indexName, pageNo, pageSize, sortAttribute, sortDirection, query);
    }

    @GetMapping(value = "${mahuta.api-spec.v1.query.search}", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody SearchResponse searchContentsByGet(
            @RequestParam(value = "index", required = false) String indexName,
            @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE_NO) int pageNo,
            @RequestParam(value = "size", required = false, defaultValue = DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(value = "sort", required = false) Optional<String> sortAttribute,
            @RequestParam(value = "dir", required = false, defaultValue = "ASC") SortDirection sortDirection,
            @RequestParam(value = "query", required = false) Optional<String> queryStr) {

        Query query = queryStr.map(Throwing.rethrowFunc(q -> this.mapper.readValue(q, Query.class))).orElse(null);

        return executeSearch(indexName, pageNo, pageSize, sortAttribute, sortDirection, query);
    }

    private SearchResponse executeSearch(String indexName, int pageNo, int pageSize, Optional<String> sortAttribute,
            SortDirection sortDirection, Query query) {

        PageRequest pageRequest = sortAttribute
                .map(s -> PageRequest.of(pageNo, pageSize, sortAttribute.get(), sortDirection))
                .orElse(PageRequest.of(pageNo, pageSize));

        return mahuta.prepareSearch()
                .indexName(indexName)
                .pageRequest(pageRequest)
                .query(query)
                .execute();
    }

}
