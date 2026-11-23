-- 서비스마다 자기 데이터베이스(한 PostgreSQL 인스턴스에 셋). 테이블은 각 서비스의 schema.sql이 만든다.
create database order_db;
create database inventory_db;
create database payment_db;
