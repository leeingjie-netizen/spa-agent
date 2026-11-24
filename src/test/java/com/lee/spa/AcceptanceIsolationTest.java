package com.lee.spa;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AcceptanceIsolationTest {
 @Test void schedulerOverrideCannotShareAnOrdinaryCachedContext() throws Exception {
  var factory=new AcceptanceIsolation();
  var ordinary=factory.createContextCustomizer(AcceptanceIsolationTest.class,java.util.List.of());
  var scheduler=factory.createContextCustomizer(Class.forName("com.lee.spa.commerce.Round2TimeoutSchedulerTest"),java.util.List.of());
  assertNotEquals(ordinary,scheduler);
 }
 @Test void rejectsBusinessAndRestoreDatabasesBeforeFixtureWrites() throws Exception {
  for(String name:new String[]{"spa","spa_restore_20261123031256","spa_acceptance_00000000000000"}){
   var connection=mock(java.sql.Connection.class);var statement=mock(java.sql.Statement.class);var result=mock(java.sql.ResultSet.class);
   when(connection.createStatement()).thenReturn(statement);when(statement.executeQuery("SELECT DATABASE(),@@port")).thenReturn(result);
   when(result.next()).thenReturn(true);when(result.getString(1)).thenReturn(name);when(result.getInt(2)).thenReturn(3306);
   assertThrows(IllegalStateException.class,()->AcceptanceIsolation.verify(connection));
  }
 }
}
