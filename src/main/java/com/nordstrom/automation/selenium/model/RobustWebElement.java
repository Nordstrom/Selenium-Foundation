package com.nordstrom.automation.selenium.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Timeouts;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.internal.FindsByCssSelector;
import org.openqa.selenium.internal.FindsByXPath;
import org.openqa.selenium.internal.WrapsElement;

import com.nordstrom.automation.selenium.SeleniumConfig.WaitType;
import com.nordstrom.automation.selenium.core.ByType;
import com.nordstrom.automation.selenium.core.JsUtility;
import com.nordstrom.automation.selenium.core.WebDriverUtils;
import com.nordstrom.automation.selenium.interfaces.WrapsContext;
import com.nordstrom.automation.selenium.support.Coordinator;
import com.nordstrom.common.base.UncheckedThrow;

/**
 * This is a wrapper class for objects that implement the {@link WebElement} interface. If a 
 * {@link StaleElementReferenceException} failure is encountered on invocation of any of the
 * {@link WebElement} methods, an attempt is made to refresh the reference and its search 
 * context chain down to the parent page object. 
 * <p>
 * This class also implements support for 'optional' elements, which provide an efficient 
 * mechanism for handling elements that may be absent in certain scenarios.
 */
public class RobustWebElement implements WebElement, WrapsElement, WrapsContext {
    
    /** wraps 1st matched reference */
    public static final int CARDINAL = -1;
    /** wraps an optional reference */
    public static final int OPTIONAL = -2;
    
    private static final String LOCATE_BY_CSS = JsUtility.getScriptResource("locateByCss.js");
    private static final String LOCATE_BY_XPATH = JsUtility.getScriptResource("locateByXpath.js");
    
    private enum Strategy { LOCATOR, JS_XPATH, JS_CSS }
    
    private WebDriver driver;
    private WebElement wrapped;
    private WrapsContext context;
    private By locator;
    private int index;
    
    private String selector;
    private Strategy strategy = Strategy.LOCATOR;
    
    private Long acquiredAt;
    
    private NoSuchElementException deferredException;
    
    /**
     * Basic robust web element constructor
     * 
     * @param context element search context
     * @param locator element locator
     */
    public RobustWebElement(WrapsContext context, By locator) {
        this(null, context, locator, CARDINAL);
    }
    
    /**
     * Constructor for wrapping an existing element reference 
     * 
     * @param element element reference to be wrapped
     * @param context element search context
     * @param locator element locator
     */
    public RobustWebElement(WebElement element, WrapsContext context, By locator) {
        this(element, context, locator, CARDINAL);
    }
    
    /**
     * Main robust web element constructor
     * 
     * @param element element reference to be wrapped (may be 'null')
     * @param context element search context
     * @param locator element locator
     * @param index element index
     */
    public RobustWebElement(WebElement element, WrapsContext context, By locator, int index) {
        
        // if specified element is already robust
        if (element instanceof RobustWebElement) {
            RobustWebElement robust = (RobustWebElement) element;
            this.acquiredAt = robust.acquiredAt;
            
            this.wrapped = robust.wrapped;
            this.context = robust.context;
            this.locator = robust.locator;
            this.index = robust.index;
        } else {
            Objects.requireNonNull(context, "[context] must be non-null");
            Objects.requireNonNull(locator, "[locator] must be non-null");
            if (index < OPTIONAL) {
                throw new IndexOutOfBoundsException("Specified index is invalid");
            }
            
            this.wrapped = element;
            this.context = context;
            this.locator = locator;
            this.index = index;
        }
        
        driver = WebDriverUtils.getDriver(this.context.getWrappedContext());
        boolean findsByCss = (driver instanceof FindsByCssSelector);
        boolean findsByXPath = (driver instanceof FindsByXPath);
        
        if ((this.index == OPTIONAL) || (this.index > 0)) {
            if (findsByXPath && ( ! (this.locator instanceof By.ByCssSelector))) {
                selector = ByType.xpathLocatorFor(this.locator);
                if (this.index > 0) selector += "[" + (this.index + 1) + "]";
                strategy = Strategy.JS_XPATH;
                
                this.locator = By.xpath(this.selector);
            } else if (findsByCss) {
                selector = ByType.cssLocatorFor(this.locator);
                if (selector != null) {
                    strategy = Strategy.JS_CSS;
                }
            }
        }
        
        if (this.wrapped == null) {
            if (this.index == OPTIONAL) {
                acquireReference(this);
            } else {
                refreshReference(null);
            }
        } else if (acquiredAt == null) {
            acquiredAt = Long.valueOf(System.currentTimeMillis());
        }
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + context.hashCode();
        result = prime * result + locator.hashCode();
        result = prime * result + index;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RobustWebElement other = (RobustWebElement) obj;
        if (!context.equals(other.context))
            return false;
        if (!locator.equals(other.locator))
            return false;
        if (index != other.index)
            return false;
        return true;
    }

    @Override
    public <X> X getScreenshotAs(final OutputType<X> arg0) {
        try {
            return getWrappedElement().getScreenshotAs(arg0);
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).getScreenshotAs(arg0);
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public void clear() {
        try {
            getWrappedElement().clear();
        } catch (StaleElementReferenceException e) {
            refreshReference(e).clear();
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public void click() {
        try {
            getWrappedElement().click();
        } catch (StaleElementReferenceException e) {
            refreshReference(e).click();
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public WebElement findElement(final By by) {
        return getElement(this, by);
    }

    @Override
    public List<WebElement> findElements(final By by) {
        return getElements(this, by);
    }

    @Override
    public String getAttribute(String name) {
        try {
            return getWrappedElement().getAttribute(name);
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).getAttribute(name);
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public String getCssValue(String propertyName) {
        try {
            return getWrappedElement().getCssValue(propertyName);
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).getCssValue(propertyName);
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public Point getLocation() {
        try {
            return getWrappedElement().getLocation();
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).getLocation();
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public Rectangle getRect() {
        try {
            return getWrappedElement().getRect();
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).getRect();
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public Dimension getSize() {
        try {
            return getWrappedElement().getSize();
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).getSize();
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public String getTagName() {
        try {
            return getWrappedElement().getTagName();
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).getTagName();
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public String getText() {
        try {
            return getWrappedElement().getText();
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).getText();
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public boolean isDisplayed() {
        try {
            return getWrappedElement().isDisplayed();
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).isDisplayed();
        } catch (NullPointerException e) {
            return false;
        }
    }

    @Override
    public boolean isEnabled() {
        try {
            return getWrappedElement().isEnabled();
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).isEnabled();
        } catch (NullPointerException e) {
            return false;
        }
    }

    @Override
    public boolean isSelected() {
        try {
            return getWrappedElement().isSelected();
        } catch (StaleElementReferenceException e) {
            return refreshReference(e).isSelected();
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public void sendKeys(CharSequence... keysToSend) {
        try {
            getWrappedElement().sendKeys(keysToSend);
        } catch (StaleElementReferenceException e) {
            refreshReference(e).sendKeys(keysToSend);
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public void submit() {
        try {
            getWrappedElement().submit();
        } catch (StaleElementReferenceException e) {
            refreshReference(e).submit();
        } catch (NullPointerException e) {
            throw deferredException();
        }
    }

    @Override
    public WebElement getWrappedElement() {
        if (wrapped == null) {
            refreshReference(null);
        }
        return wrapped;
    }
    
    /**
     * Determine if this robust element wraps a valid reference.
     * 
     * @return 'true' if reference was acquired; otherwise 'false'
     */
    public boolean hasReference() {
        if ((index == OPTIONAL) && (wrapped == null)) {
            acquireReference(this);
            return (null != wrapped);
        } else {
            return true;
        }
    }
    
    /**
     * Get the search context for this element.
     * 
     * @return element search context
     */
    public WrapsContext getContext() {
        return context;
    }
    
    /**
     * Get the locator for this element.
     * 
     * @return element locator
     */
    public By getLocator() {
        return locator;
    }
    
    /**
     * Get the element index.
     * <p>
     * <b>NOTE</b>: {@link #CARDINAL} = 1st matched reference; {@link #OPTIONAL} = an optional reference
     * 
     * @return element index (see NOTE)
     */
    public int getIndex() {
        return index;
    }
    
    /**
     * Refresh the wrapped element reference.
     * 
     * @param refreshTrigger {@link StaleElementReferenceException} that necessitates reference refresh
     * @return this robust web element with refreshed reference
     */
    WebElement refreshReference(final StaleElementReferenceException refreshTrigger) {
        try {
            WaitType.IMPLIED.getWait((SearchContext) context).until(referenceIsRefreshed(this));
            return this;
        } catch (TimeoutException e) {
            if (refreshTrigger == null) {
                throw UncheckedThrow.throwUnchecked(e.getCause());
            }
        } catch (WebDriverException e) {
            if (refreshTrigger == null) {
                throw e;
            }
        }
        throw refreshTrigger;
    }
    
    /**
     * Returns a 'wait' proxy that refreshes the wrapped reference of the specified robust element.
     * 
     * @param element robust web element object
     * @return wrapped element reference (refreshed)
     */
    private static Coordinator<WebElement> referenceIsRefreshed(final RobustWebElement element) {
        return new Coordinator<WebElement>() {

            @Override
            public WebElement apply(SearchContext context) {
                try {
                    return acquireReference(element);
                } catch (StaleElementReferenceException e) {
                    ((WrapsContext) context).refreshContext(((WrapsContext) context).acquiredAt());
                    return acquireReference(element);
                }
            }

            @Override
            public String toString() {
                return "element reference to be refreshed";
            }
        };
        
    }
    
    /**
     * Acquire the element reference that's wrapped by the specified robust element.
     * 
     * @param element robust web element object
     * @return wrapped element reference
     */
    private static WebElement acquireReference(RobustWebElement element) {
        SearchContext context = element.context.getWrappedContext();
        
        if (element.strategy == Strategy.LOCATOR) {
            Timeouts timeouts = element.driver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
            try {
                if (element.index > 0) {
                    List<WebElement> elements = context.findElements(element.locator);
                    if (element.index < elements.size()) {
                        element.wrapped = elements.get(element.index);
                    } else {
                        throw new NoSuchElementException(
                                String.format("Too few elements located %s: need: %d; have: %d", 
                                        element.locator, element.index + 1, elements.size()));
                    }
                } else {
                    element.wrapped = context.findElement(element.locator);
                }
            } catch (NoSuchElementException e) {
                if (element.index != OPTIONAL) throw e;
                element.deferredException = e;
                element.wrapped = null;
            } finally {
                timeouts.implicitlyWait(WaitType.IMPLIED.getInterval(), TimeUnit.SECONDS);
            }
        } else {
            List<Object> args = new ArrayList<>();
            List<WebElement> contextArg = new ArrayList<>();
            if (context instanceof WebElement) contextArg.add((WebElement) context);
            
            String js;
            args.add(contextArg);
            args.add(element.selector);
            
            if (element.strategy == Strategy.JS_XPATH) {
                js = LOCATE_BY_XPATH;
            } else {
                js = LOCATE_BY_CSS;
                args.add(element.index);
            }
            
            element.wrapped = JsUtility.runAndReturn(element.driver, js, args.toArray());
        }
        
        if (element.wrapped != null) {
            element.acquiredAt = System.currentTimeMillis();
            element.deferredException = null;
        }
        
        return element;
    }
    
    @Override
    public SearchContext getWrappedContext() {
        return getWrappedElement();
    }

    @Override
    public SearchContext refreshContext(Long expiration) {
        // refresh wrapped element reference if it's past the expiration
        return (expiration.compareTo(acquiredAt()) >= 0) ? refreshReference(null) : this;
    }

    @Override
    public Long acquiredAt() {
        return acquiredAt;
    }
    
    @Override
    public WebDriver getWrappedDriver() {
        return WebDriverUtils.getDriver(getWrappedElement());
    }
    
    /**
     * Get a wrapped reference to the first element matching the specified locator.
     * <p>
     * <b>NOTE</b>: Use {@link RobustWebElement#hasReference()} to determine if a valid reference was acquired.
     * 
     * @param by the locating mechanism
     * @return robust web element
     */
    public RobustWebElement findOptional(By by) {
        return RobustWebElement.getElement(this, by, RobustWebElement.OPTIONAL);
    }
    
    /**
     * Get the list of elements that match the specified locator in the indicated context.
     * 
     * @param context element search context
     * @param locator element locator
     * @return list of robust elements in context that match the locator
     */
    public static List<WebElement> getElements(WrapsContext context, By locator) {
        List<WebElement> elements;
        try {
            elements = context.getWrappedContext().findElements(locator);
            for (int index = 0; index < elements.size(); index++) {
                elements.set(index, new RobustWebElement(elements.get(index), context, locator, index));
            }
        } catch (StaleElementReferenceException e) {
            elements = context.refreshContext(context.acquiredAt()).findElements(locator);
        }
        return elements;
    }
    
    /**
     * Get the first element that matches the specified locator in the indicated context.
     * 
     * @param context element search context
     * @param locator element locator
     * @return robust element in context that matches the locator
     */
    public static RobustWebElement getElement(WrapsContext context, By locator) {
        return getElement(context, locator, CARDINAL);
    }
    
    /**
     * Get the item at the specified index in the list of elements matching the specified 
     * locator in the indicated context.
     * 
     * @param context element search context
     * @param locator element locator
     * @param index element index
     * @return indexed robust element in context that matches the locator
     */
    public static RobustWebElement getElement(WrapsContext context, By locator, int index) {
        return new RobustWebElement(null, context, locator, index);
    }
    
    /**
     * Throw the deferred exception that was stored upon failing to acquire the reference for an optional element.<br>
     * <b>NOTE</b>: The deferred exception is not thrown directly - it's wrapped in a NullPointerException to indicate
     * that the failure was caused by utilizing an optional element for which no actual reference could be acquired.
     * 
     * @return nothing (always throws deferred exception wrapped in NullPointerException)
     */
    private NullPointerException deferredException() {
        throw (NullPointerException) new NullPointerException("Unable to acquire reference for optional element")
                .initCause(deferredException);
    }

    @Override
    public SearchContext switchTo() {
        return context.switchTo();
    }
}
