package eatsworkflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.DataConverterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;

public class JacksonDataConverter implements DataConverter {
    private static final Logger logger = LoggerFactory.getLogger(JacksonDataConverter.class);
    private final ObjectMapper objectMapper;

    public JacksonDataConverter() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public byte[] toData(Object... values) throws DataConverterException {
        try {
            if (values == null || values.length == 0) {
                return new byte[0];
            }
            // If only one value, serialize it directly, else serialize as array
            if (values.length == 1) {
                return objectMapper.writeValueAsBytes(values[0]);
            } else {
                return objectMapper.writeValueAsBytes(values);
            }
        } catch (IOException e) {
            throw new DataConverterException("Failed to serialize value(s): " + Arrays.toString(values), e);
        }
    }

    @Override
    public <T> T fromData(byte[] content, Class<T> valueClass, Type valueType) throws DataConverterException {
        try {
            if (valueType != null) {
                return objectMapper.readValue(content, TypeFactory.defaultInstance().constructType(valueType));
            } else {
                return objectMapper.readValue(content, valueClass);
            }
        } catch (IOException e) {
            throw new DataConverterException("Failed to deserialize content to " + valueClass + " with type " + valueType, e);
        }
    }

    @Override
    public Object[] fromDataArray(byte[] content, Type... valueTypes) {
        try {
            if (content == null || content.length == 0) {
                return new Object[0];
            }
            String contentStr = new String(content);
            // Try to parse as a JSON array first
            if (contentStr.trim().startsWith("[") && contentStr.trim().endsWith("]")) {
                Object[] result = objectMapper.readValue(content, Object[].class);
                if (valueTypes != null && valueTypes.length > 0) {
                    Object[] typedResult = new Object[valueTypes.length];
                    for (int i = 0; i < Math.min(valueTypes.length, result.length); i++) {
                        if (result[i] != null) {
                            typedResult[i] = objectMapper.convertValue(result[i], objectMapper.constructType(valueTypes[i]));
                        }
                    }
                    return typedResult;
                }
                return result;
            } else {
                Object singleValue = objectMapper.readValue(content, Object.class);
                if (valueTypes != null && valueTypes.length > 0) {
                    Object typedValue = objectMapper.convertValue(singleValue, objectMapper.constructType(valueTypes[0]));
                    return new Object[]{typedValue};
                }
                return new Object[]{singleValue};
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize data array", e);
        }
    }
} 