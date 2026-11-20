package com.lee.spa.commerce;
import com.lee.spa.common.ImageUrlPolicy;
import com.lee.spa.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BugfixInputTest {
 @Test void ossSignedUrlIsPreservedAndInvalidSchemesAreRejected(){
  String url="https://demo.oss-cn-hangzhou.aliyuncs.com/image.png?Expires=123&Signature=a%2Fb%3D";
  assertEquals(url,ImageUrlPolicy.validate(" "+url+" ",500,false));
  for(String value:new String[]{"http://host/a.png","https://","https://user:secret@host/a.png","javascript:alert(1)"})assertThrows(BusinessException.class,()->ImageUrlPolicy.validate(value,500,false));
  assertEquals("",ImageUrlPolicy.validate(null,500,false));
 }
}
