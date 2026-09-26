package io.github.dimazigel.yfinance.http;

import feign.DefaultInvocationHandlerFactory;
import feign.FeignException;
import feign.InvocationHandlerFactory;
import feign.Target;
import feign.codec.DecodeException;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * The proxy boundary: Feign wraps any exception thrown by a decoder in a {@link DecodeException};
 * this unwraps the library's own exceptions again and turns any other Feign failure into a
 * {@link YFDataException}, so callers only ever see {@link YFinanceException}s.
 */
final class YahooInvocationHandlerFactory implements InvocationHandlerFactory {

    private final InvocationHandlerFactory delegate = new DefaultInvocationHandlerFactory();

    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
        InvocationHandler handler = delegate.create(target, dispatch);
        return (proxy, method, args) -> {
            try {
                return handler.invoke(proxy, method, args);
            } catch (DecodeException e) {
                if (e.getCause() instanceof YFinanceException ours) {
                    throw ours;
                }
                throw new YFDataException("Yahoo Finance response could not be decoded: " + e.getMessage(), e);
            } catch (FeignException e) {
                throw new YFDataException("Yahoo Finance call failed: " + e.getMessage(), e);
            }
        };
    }
}
