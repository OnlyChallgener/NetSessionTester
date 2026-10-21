#import <UIKit/UIKit.h>
#import <shared/shared.h>

@interface SceneDelegate : UIResponder <UIWindowSceneDelegate, UITabBarControllerDelegate>
@property (strong, nonatomic) UIWindow *window;
@end

@implementation SceneDelegate

- (void)scene:(UIScene *)scene willConnectToSession:(UISceneSession *)session options:(UISceneConnectionOptions *)options {
    if (![scene isKindOfClass:UIWindowScene.class]) return;
    self.window = [[UIWindow alloc] initWithWindowScene:(UIWindowScene *)scene];
    UITabBarController *tabs = [[UITabBarController alloc] init];
    NSArray<UIViewController *> *pages = @[
        [NSTAppFactory.shared mainController], [NSTAppFactory.shared toolsController],
        [NSTAppFactory.shared historyController], [NSTAppFactory.shared settingsController]
    ];
    NSArray<NSString *> *titles = @[@"测试", @"工具", @"历史", @"设置"];
    NSArray<NSString *> *symbols = @[@"waveform.path.ecg", @"wrench.and.screwdriver", @"clock", @"gearshape"];
    [pages enumerateObjectsUsingBlock:^(UIViewController *page, NSUInteger index, BOOL *stop) {
        page.title = titles[index];
        page.tabBarItem = [[UITabBarItem alloc] initWithTitle:titles[index]
                                                    image:[UIImage systemImageNamed:symbols[index]]
                                                      tag:(NSInteger)index];
    }];
    tabs.viewControllers = pages;
    tabs.delegate = self;
    // Standard UIKit bars adopt Liquid Glass with the iOS 26 SDK. The system owns
    // accessibility and older-system fallbacks; data cards remain in the content layer.
    self.window.rootViewController = tabs;
    [self.window makeKeyAndVisible];
}

- (BOOL)tabBarController:(UITabBarController *)tabs shouldSelectViewController:(UIViewController *)viewController {
    if (tabs.selectedViewController != viewController) [NSTAppFactory.shared stopActiveTests];
    return YES;
}

- (void)sceneDidEnterBackground:(UIScene *)scene {
    [NSTAppFactory.shared stopActiveTests];
    UIApplication.sharedApplication.idleTimerDisabled = NO;
}

- (void)sceneDidDisconnect:(UIScene *)scene {
    [NSTAppFactory.shared stopActiveTests];
    UIApplication.sharedApplication.idleTimerDisabled = NO;
}
@end

@interface AppDelegate : UIResponder <UIApplicationDelegate>
@end
@implementation AppDelegate
- (UISceneConfiguration *)application:(UIApplication *)application
        configurationForConnectingSceneSession:(UISceneSession *)session
        options:(UISceneConnectionOptions *)options {
    UISceneConfiguration *configuration = [[UISceneConfiguration alloc] initWithName:@"Default" sessionRole:session.role];
    configuration.delegateClass = SceneDelegate.class;
    return configuration;
}
@end

int main(int argc, char * argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass([AppDelegate class]));
    }
}
