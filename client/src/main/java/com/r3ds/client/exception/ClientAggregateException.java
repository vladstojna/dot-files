package com.r3ds.client.exception;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ClientAggregateException extends Exception {

	private static final long serialVersionUID = 1L;

	private List<Throwable> basket = null;

	public ClientAggregateException() {
		super();
		basket = new ArrayList<>();
	}

	public ClientAggregateException(String message) {
		super(message);
		basket = new ArrayList<>();
	}

	public ClientAggregateException(Collection<Throwable> causes) {
		super();
		basket = new ArrayList<>(causes);
	}

	public ClientAggregateException(Throwable ...causes) {
		this(Arrays.asList(causes));
	}

	public ClientAggregateException(String message, Collection<Throwable> causes) {
		super(message);
		basket = new ArrayList<>(causes);
	}

	public ClientAggregateException(String message, Throwable ...causes) {
		this(message, Arrays.asList(causes));
	}

	public Collection<Throwable> getCauses() {
		return basket;
	}

	public String[] getMessages() {
		String[] messages = new String[basket.size()];
		for(int ix = 0 ; ix < basket.size() ; ++ix) {
			messages[ix] = basket.get(ix).getMessage();
		}
		return messages;
	}

	public String getAggregatedMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getMessage());
		for(Throwable t : basket) {
			sb.append('\n').append(t.getMessage());
		}
		return sb.toString();
	}
}
