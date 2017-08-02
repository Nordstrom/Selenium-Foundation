**Selenium Foundation** includes a collection of utility methods and support script files that make it easy to leverage the power of JavaScript in your test suites. **JsUtility** is found in the <span style="color:blue">_com.nordstrom.automation.selenium.core_</span> package, and the script files are found in <span style="color:blue">_src/main/resources_</span>. **JsUtility** and its supporting scripts provide the following capabilities:

*   Run JavaScript (with or without result)
*   Load script files as strings
*   Throw Java exceptions from JavaScript functions

# Run JavaScript (with or without result)

**Selenium Foundation** enables you to execute JavaScript in the context of the currently selected frame or window. The script fragment provided will be executed as the body of an anonymous function.

Within the script, use <span style="color:blue">**`document`**</span> to refer to the current document. Note that local variables will not be available once the script has finished executing, though global variables will persist.

If the script has a return value (i.e. if the script contains a <span style="color:blue">**`return`**</span> statement), then the following steps will be taken:

*   For an HTML element, a WebElement is returned
*   For a decimal, a Double is returned
*   For a non-decimal number, a Long is returned
*   For a boolean, a Boolean is returned
*   For all other cases, a String is returned.
*   For an array, return a List<Object> with each object following the rules above. We support nested lists.
*   Unless the value is null or there is no return value, in which null is returned

Arguments must be a number, a boolean, a String, WebElement, or a List of any combination of the above. An exception will be thrown if the arguments do not meet these criteria. The arguments will be made available to the JavaScript via the "arguments" magic variable, as if the function were called via "Function.apply".

###### Run JavaScript with Result
```java
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import com.nordstrom.automation.selenium.core.JsUtility;
 
public class JavaScriptExample {
 
    /**
     * This example executes an anonymous function that accepts an argument.<br>
     * NOTE: Script file <getMetaTagByName.js> can be found below.
     *
     * @param driver Selenium driver
     * @param name name of target meta tag
     * @return meta element with desired name; 'null' if not found
     */
    public String runAnonymousJavaScriptFunctionWithArgument(WebDriver driver, String name) {
        // Get script text from resource file <getMetaTagByName.js>.
        String script = JsUtility.getScriptResource("getMetaTagByName.js");
        // Execute script as anonymous function, passing specified argument
        WebElement response = JsUtility.runAndReturn(driver, script, WebElement.class, "viewport");
        // If element reference was returned, extract 'content' attribute
        return (response == null) ? null : response.getAttribute("content");
    }
}
```

This is sample JavaScript file &lt;getMetaTagByName.js&gt;. This file can be stored anywhere on the class path, typically a 'resources' folder within the project hierarchy.

###### getMetaTabByName.js
```javascript
var found = document.getElementsByTagName("meta");
for (var i = 0; i < found.length; i++) {
    if (found[i].getAttribute("name") == arguments[0]) return found[i];
}
return null;
```

# Load script files as strings

**JsUtility** includes the **`getScriptResource()`** method that enables you to load the contents of files in your project as strings. These files can be stored anywhere on the class path, typically a 'resources' folder within the project hierarchy. The following snippet is an excerpt from the previous example: 

###### Get script resource &lt;getMetaTagByName.js&gt;
```java
        ...
        // Get script text from resource file <getMetaTagByName.js>.
        String script = JsUtility.getScriptResource("getMetaTagByName.js");
        ...
```

# Throw Java exceptions from JavaScript functions

This snippet of Java code injects the glue library into the current page and invokes a JavaScript function that may throw an exception.

###### Invoke JavaScript that throws an exception
```java
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import com.nordstrom.automation.selenium.core.JsUtility;
 
public class AnotherJavaScriptExample {
 
    /**
     * This example executes an anonymous function that accepts an argument.<br>
     * NOTE: Script file <requireMetaTagByName.js> can be found below.
     *
     * @param driver Selenium driver
     * @param name name of target meta tag
     * @return meta element with desired name
     */
    public String runJavaScriptFunctionThrowsException(WebDriver driver, String name) {
        // Inject Java glue library
        JsUtility.injectGlueLib(driver)
        // Get script text from resource file <requireMetaTagByName.js>.
        String script = JsUtility.getScriptResource("requireMetaTagByName.js");
         
        try {
            // Execute script as anonymous function, passing specified argument
            WebElement response = JsUtility.runAndReturn(driver, script, WebElement.class, name);
            // Extract 'content' attribute
            return response.getAttribute("content");
        } catch (WebDriverException e) {
            // Extract encoded exception
            throw JsUtility.propagate(e);
        }
    }
}
```

This is the sample JavaScript file &lt;requireMetaTagByName.js&gt;. This file can be stored anywhere on the class path, typically a 'resources' folder within the project hierarchy.

###### requireMetaTabByName.js
```javascript
var found = document.getElementsByTagName("meta");
for (var i = 0; i < found.length; i++) {
    if (found[i].getAttribute("name") == arguments[0]) return found[i];
}
throwNew('org.openqa.selenium.NoSuchElementException', 'No meta element found with name: ' + arguments[0]);
```