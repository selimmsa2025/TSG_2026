package com.example.demo.agent;

public enum RouteType {
    CHAT,
    RAG,
    TOOL
}

//0309추가 
// 질문을 보고 route를 정할 때 기준값
// enum 정해진 값만 가질수 있는 타임 String 으로 하지 않은 이유는 if ("CHAT".equals(route)) { ... } 와 같이 문자열 비교가 많아짐 / switch 문 쓰기 좋음