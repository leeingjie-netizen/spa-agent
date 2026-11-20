package com.lee.spa.common;
import com.lee.spa.common.exception.BusinessException;
import java.net.URI;
/** Validation only; never fetch administrator URLs on the server. */
public final class ImageUrlPolicy {
 private ImageUrlPolicy(){}
 public static String validate(String raw,int max,boolean local){
  if(raw==null||raw.isBlank())return "";
  String value=raw.strip();
  if(value.length()>max)throw new BusinessException("图片地址过长，最多"+max+"字符");
  if(local&&value.startsWith("/assets/")&&!value.contains(".."))return value;
  try{URI uri=URI.create(value);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();}
  catch(IllegalArgumentException error){throw new BusinessException("图片请使用可公开访问的有效HTTPS地址");}
  return value;
 }
}
