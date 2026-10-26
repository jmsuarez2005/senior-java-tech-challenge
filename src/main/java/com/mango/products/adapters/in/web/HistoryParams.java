package com.mango.products.adapters.in.web;

record HistoryParams(String from, String to, String sort, int page, int size) {

	static final String DEFAULT_SORT = "initDate,asc";
	static final HistoryParams WHOLE_HISTORY = new HistoryParams(null, null, DEFAULT_SORT, 0, 0);

}
