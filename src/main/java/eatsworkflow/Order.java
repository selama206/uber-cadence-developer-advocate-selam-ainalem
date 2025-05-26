package eatsworkflow;

import java.util.List;
import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(Order.class);
    
    private String id;
    private List<String> content;

    @JsonCreator
    public Order(
        @JsonProperty("id") String id,
        @JsonProperty("content") List<String> content
    ) {
        logger.info("Creating Order with id: {} and content: {}", id, content);
        this.id = id;
        this.content = content;
    }

    // Default constructor for serialization
    public Order() {
        logger.info("Creating empty Order");
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        logger.info("Setting Order id to: {}", id);
        this.id = id;
    }

    @JsonProperty("content")
    public List<String> getContent() {
        return content;
    }

    @JsonProperty("content")
    public void setContent(List<String> content) {
        logger.info("Setting Order content to: {}", content);
        this.content = content;
    }

    @Override
    public String toString() {
        return String.format("Order{id='%s', content=%s}", id, content);
    }
} 