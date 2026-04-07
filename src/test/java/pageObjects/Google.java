package pageObjects;

import org.openqa.selenium.By;


public class Google {


    public static By search = By.name("q");
    public static By list = By.xpath("//ul[@role='listbox']/li[1]");


}
