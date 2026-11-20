const {test}=require('node:test'),assert=require('node:assert/strict'),{validateFixtureOrder}=require('./fixture-ownership.cjs')
const manifest={fixtures:{orderIds:['101'],users:['201','202'],appointmentIds:['301'],staffId:'401',serviceId:'501'}}
const order={id:'101',userId:'202',appointmentId:'301',staffId:'401',serviceId:'501'}
test('either manifest user may win the concurrent booking',()=>{assert.doesNotThrow(()=>validateFixtureOrder(manifest,order));assert.doesNotThrow(()=>validateFixtureOrder(manifest,{...order,userId:'201'}))})
test('a foreign owner cannot be retired',()=>{assert.throws(()=>validateFixtureOrder(manifest,{...order,userId:'999'}),/owner mismatch/)})
test('orders and appointments outside the manifest are rejected',()=>{assert.throws(()=>validateFixtureOrder(manifest,{...order,id:'999'}));assert.throws(()=>validateFixtureOrder(manifest,{...order,appointmentId:'999'}))})
test('fixture names alone never authorize unrelated staff or services',()=>{assert.throws(()=>validateFixtureOrder(manifest,{...order,staffId:'999'}));assert.throws(()=>validateFixtureOrder(manifest,{...order,serviceId:'999'}))})
