package com.lee.spa;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
/** Standalone JRE-only readiness check: no package-manager dependency in runtime image. */
public final class ContainerHealthProbe {
 public static void main(String[] args) {
  try {
   var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
   var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/health/ready")).timeout(Duration.ofSeconds(3)).GET().build();
   System.exit(client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode()==200?0:1);
  }catch(Exception unavailable){System.exit(1);}
 }
}
