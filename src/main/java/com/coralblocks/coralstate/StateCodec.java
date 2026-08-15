package com.coralblocks.coralstate;

import com.coralblocks.coralproto.Proto;

public interface StateCodec<T, P extends Proto> {

	public Class<T> javaType();

	public P getProto();

	public void encode(T object, P proto);

	public void decode(P proto, T object);
}
