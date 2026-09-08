#import <UIKit/UIKit.h>
#import <objc/runtime.h>
#import <dlfcn.h>

@interface AppDelegate : UIResponder <UIApplicationDelegate>
@property (strong, nonatomic) UIWindow *window;
@end

@implementation AppDelegate

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    self.window = [[UIWindow alloc] initWithFrame:[[UIScreen mainScreen] bounds]];

    UIViewController *vc = nil;

    // 1. Try resolving pure C function exported via @CName("createMainViewController")
    typedef UIViewController* (*CreateVcFn)(void);
    CreateVcFn createFn = (CreateVcFn)dlsym(RTLD_DEFAULT, "createMainViewController");
    if (createFn != NULL) {
        vc = createFn();
    }

    // 2. Try resolving Objective-C class dynamically without hard compile-time symbol dependency
    if (vc == nil) {
        NSArray<NSString *> *candidateClasses = @[
            @"IosAppKt",
            @"SharedIosAppKt",
            @"sharedIosAppKt",
            @"SharedMainKt"
        ];
        for (NSString *name in candidateClasses) {
            Class cls = NSClassFromString(name);
            if (cls && [cls respondsToSelector:@selector(MainViewController)]) {
                vc = [cls performSelector:@selector(MainViewController)];
                break;
            }
        }
    }

    // 3. Fallback placeholder
    if (vc == nil) {
        vc = [[UIViewController alloc] init];
        vc.view.backgroundColor = [UIColor blackColor];
    }

    self.window.rootViewController = vc;
    [self.window makeKeyAndVisible];
    return YES;
}

@end

int main(int argc, char * argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass([AppDelegate class]));
    }
}
