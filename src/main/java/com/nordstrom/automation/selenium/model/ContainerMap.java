package com.nordstrom.automation.selenium.model;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import com.nordstrom.common.base.UncheckedThrow;

abstract class ContainerMap<V extends ComponentContainer> extends AbstractMap<Object, V> {
    
    protected ComponentContainer parent;
    protected Class<V> containerType;
    protected By locator;
    protected Method method;
    
    private List<WebElement> elementList;
    private Entry<V>[] table;
    private Set<Map.Entry<Object, V>> entrySet;
    private int size;
    
    @SuppressWarnings("unchecked")
    ContainerMap(ComponentContainer parent, Class<V> containerType, By locator) {
        Objects.requireNonNull(parent, "[parent] must be non-null");
        Objects.requireNonNull(containerType, "[containerType] must be non-null");
        Objects.requireNonNull(locator, "[locator] must be non-null");
        
        ComponentContainer.verifyCollectible(containerType);
        
        this.parent = parent;
        this.containerType = containerType;
        this.locator = locator;
        
        method = ComponentContainer.getKeyMethod(containerType);
        
        elementList = parent.findElements(locator);
        size = elementList.size();
        table = new Entry[size];
        
        int i = size;
        Entry<V> next = null;
        while (i-- > 0) {
            next = table[i] = new Entry<V>(this, (RobustWebElement) elementList.get(i), next);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public boolean containsValue(Object value) {
        if (value.getClass() == containerType) {
            V container = (V) value;
            SearchContext context = container.getContext();
            return elementList.contains(context);
        }
        return false;
    }
    
    @Override
    public Set<Map.Entry<Object, V>> entrySet() {
        Set<Map.Entry<Object, V>> es;
        return (es = entrySet) == null ? (entrySet = new EntrySet()) : es;
    }
    
    /**
     * Get table entry for the specified key.
     * 
     * @param key key of desired entry
     * @return entry for the specified key; 'null' if not found
     */
    final Entry<V> getEntry(Object key) {
        Entry<V>[] tab; Entry<V> first, e; Object k;
        if ((tab = table) != null && tab.length > 0 &&
            (first = tab[0]) != null) {
            if ((k = first.key) == key || (key != null && key.equals(k)))
                return first;
            if ((e = first.next) != null) {
                do {
                    if ((k = e.key) == key || (key != null && key.equals(k)))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;
    }
    
    /**
     * Get array of constructor argument types.
     * 
     * @return array of constructor argument types
     */
    Class<?>[] getArgumentTypes() {
        return ComponentContainer.SIGNATURE;
    }
    
    /**
     * Get array of constructor argument values for the specified context element.
     * 
     * @param element container map context element
     * @return array of constructor argument values
     */
    Object[] getArguments(WebElement element) {
        return new Object[] {(RobustWebElement) element, parent};
    }
    
    static class Entry<V extends ComponentContainer> implements Map.Entry<Object, V> {
        ContainerMap<V> map;
        RobustWebElement element;
        Entry<V> next;
        Object key;
        V value;

        Entry(ContainerMap<V> map, RobustWebElement element, Entry<V> next) {
            this.map = map;
            this.element = element;
            this.next = next;
            
            try {
                key = map.method.invoke(null, element);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw UncheckedThrow.throwUnchecked(e);
            }
        }
        
        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public V getValue() {
            V v = value;
            if (v == null) {
                Class<?>[] argumentTypes = map.getArgumentTypes();
                Object[] arguments = map.getArguments(element);
                v = ComponentContainer.newContainer(map.containerType, argumentTypes, arguments);
                v = v.enhanceContainer(v);
                value = v;
            }
            return v;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }
    }
    
    class EntrySet extends AbstractSet<Map.Entry<Object, V>> {
        
        @Override
        public final int size()                 {
            return size;
        }
        
        @Override
        public final Iterator<Map.Entry<Object, V>> iterator() {
            return new EntryIterator();
        }
        
        @Override
        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            Object key = e.getKey();
            Entry<V> candidate = getEntry(key);
            return candidate != null && candidate.equals(e);
        }
        
        @Override
        public final void clear() {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public final boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }
    }
    
    class EntryIterator implements Iterator<Map.Entry<Object, V>> {
        Entry<V> next;
        Entry<V> current;
        int index;
        
        EntryIterator() {
            Entry<V>[] t = table;
            if (t != null && size > 0) {
                do {} while (index < t.length && (next = t[index++]) == null);
            }
        }
        
        @Override
        public final boolean hasNext() {
            return next != null;
        }

        @Override
        public Entry<V> next() {
            return nextEntry();
        }
        
        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }
        
        /**
         * Get the next table entry.
         * 
         * @return next table entry
         */
        final Entry<V> nextEntry() {
            Entry<V>[] t;
            Entry<V> e = next;
            if (e == null) throw new NoSuchElementException();
            if ((next = (current = e).next) == null && (t = table) != null) {
                do {} while (index < t.length && (next = t[index++]) == null);
            }
            return e;
        }
    }
}
