package com.nordstrom.automation.selenium.listener;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import com.nordstrom.automation.selenium.annotations.NoDriver;
import com.nordstrom.automation.selenium.listeners.DriverManager;
import com.nordstrom.automation.testng.ExecutionFlowController;

@Test(threadPoolSize = 10)
@Listeners({ExecutionFlowController.class, DriverManager.class})
public class DriverManagerTest {
	
	@BeforeMethod(groups = {"WithDriverBefore"})
	public void beforeMethodWithDriver() {
		Assert.assertNotNull(DriverManager.getDriver(), "Driver should have been created");
	}
	
	@Test(groups = {"WithDriverBefore"})
	@NoDriver
	public void testWithDriverBefore() {
		Assert.assertNotNull(DriverManager.getDriver(), "Driver should have been created");
	}
	
}
