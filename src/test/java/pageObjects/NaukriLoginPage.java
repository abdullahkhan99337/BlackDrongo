package pageObjects;

import org.openqa.selenium.By;

public class NaukriLoginPage {

    public static final By loginButton = By.id("login_Layer");
    public static final By usernameInput = By.xpath("//input[contains(@placeholder,'Email ID') or contains(@placeholder,'Username')]");
    public static final By passwordInput = By.xpath("//input[@type='password' and contains(@placeholder,'password')]");
    public static final By submitLoginButton = By.xpath("//button[contains(@class,'loginButton') or @type='submit']");
    public static final By loginErrorMessage = By.xpath("//*[contains(@class,'error') or contains(@class,'server-err')][contains(translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'match') or contains(translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'invalid')]");
    public static final By userProfileMenu = By.xpath("//a[contains(.,\"View profile\")]");
    public static final By uploadResumeButton = By.xpath("//button[contains(.,'Upload Resume') or contains(.,'Upload resume') or contains(.,'Upload CV') or contains(@class,'upload')] | //input[@value='Update resume' and @type='button']");
    public static final By resumeFileInput = By.xpath("//input[@type='file' and (contains(@id,'upload') or contains(@name,'upload') or contains(@class,'upload'))] | //input[@type='file']");
    public static final By resumeUploadSuccessMessage = By.xpath("//*[contains(@class,'toast') or contains(@class,'snackbar') or contains(@class,'msg')][contains(translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'resume') and (contains(translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'uploaded') or contains(translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'updated') or contains(translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'success'))]");
}
