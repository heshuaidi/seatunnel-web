import { AvatarDropdown, AvatarName, Footer, SelectLang } from "@/components";
import type { Settings as LayoutSettings } from "@ant-design/pro-components";
import { SettingDrawer } from "@ant-design/pro-components";
import "@ant-design/v5-patch-for-react-19";
import type { RequestConfig, RunTimeLayoutConfig } from "@umijs/max";
import { history } from "@umijs/max";
import "d3-transition";
import defaultSettings from "../config/defaultSettings";
import {
  BI,
  GlobalSearch,
  Knowledge,
  OpenAPI,
} from "./components/RightContent";
import ThemeSwitch from "./components/RightContent/ThemeSwitch";
import { errorConfig } from "./requestErrorConfig";
import { API_BASE, withApiBase } from "./utils/apiBase";
import { isFrontendPath, withFrontendBase } from "./utils/frontendBase";
import HttpUtils from "./utils/HttpUtils";

const isDev = process.env.NODE_ENV === "development";
const loginPath = "/login";

/**
 * @see https://umijs.org/docs/api/runtime-config#getinitialstate
 * */
export async function getInitialState(): Promise<{
  settings?: Partial<LayoutSettings>;
  currentUser?: API.CurrentUser;
  loading?: boolean;
  fetchUserInfo?: () => Promise<API.CurrentUser | undefined>;
}> {
  const fetchUserInfo = async () => {
    try {
      const msg = await HttpUtils.get<API.CurrentUser | undefined>(
        withApiBase(process.env.CURRENT_USER_API as string)
      );

      return msg.data;
    } catch (_error) {
      if (!isFrontendPath(window.location.pathname, loginPath)) {
        history.push(loginPath);
      }
    }
    return undefined;
  };
  // 如果不是登录页面，执行
  const { location } = history;
  if (!isFrontendPath(location.pathname, loginPath)) {
    const currentUser = await fetchUserInfo();
    return {
      fetchUserInfo,
      currentUser,
      settings: defaultSettings as Partial<LayoutSettings>,
    };
  }
  return {
    fetchUserInfo,
    settings: defaultSettings as Partial<LayoutSettings>,
  };
}

// ProLayout 支持的api https://procomponents.ant.design/components/layout
export const layout: RunTimeLayoutConfig = ({
  initialState,
  setInitialState,
}) => {
  return {
    menuProps: {
      defaultOpenKeys: ["/sync"],
    },
    actionsRender: () => [
      <GlobalSearch key="globalsearch" />,
      // <OpenAPI key="open-api" />,
      <Knowledge key="knowledge" />,
      <BI key="bi" />,
      <ThemeSwitch key="theme-switch" />,
      <SelectLang key="SelectLang" />,
    ],
    avatarProps: {
      src: initialState?.currentUser?.avatar,
      title: <AvatarName />,
      render: (_, avatarChildren) => {
        return <AvatarDropdown>{avatarChildren}</AvatarDropdown>;
      },
    },
    waterMarkProps: {
      content: initialState?.currentUser?.name,
    },
    footerRender: () => <Footer />,
    onPageChange: () => {
      const { location } = history;
      console.log(initialState?.currentUser);
      // 如果没有登录，重定向到 login
      if (
        !initialState?.currentUser &&
        !isFrontendPath(location.pathname, loginPath)
      ) {
        history.push(loginPath);
      }
    },
    bgLayoutImgList: [
      {
        src: withFrontendBase("/offline-assets/alipay-d2lw.png"),
        left: 85,
        bottom: 100,
        height: "303px",
      },
      {
        src: withFrontendBase("/offline-assets/alipay-c2tw.png"),
        bottom: -68,
        right: -45,
        height: "303px",
      },
      {
        src: withFrontendBase("/offline-assets/alipay-f6vs.png"),
        bottom: 0,
        left: 0,
        width: "331px",
      },
    ],
    links: [],
    menuHeaderRender: undefined,
    // 自定义 403 页面
    // unAccessible: <div>unAccessible</div>,
    // 增加一个 loading 的状态
    childrenRender: (children) => {
      // if (initialState?.loading) return <PageLoading />;
      return (
        <>
          {children}
          {isDev && (
            <SettingDrawer
              disableUrlParams
              enableDarkTheme
              settings={initialState?.settings}
              onSettingChange={(settings) => {
                setInitialState((preInitialState) => ({
                  ...preInitialState,
                  settings,
                }));
              }}
            />
          )}
        </>
      );
    },
    ...initialState?.settings,
  };
};

/**
 * @name request 配置，可以配置错误处理
 * 它基于 axios 和 ahooks 的 useRequest 提供了一套统一的网络请求和错误处理方案。
 * @doc https://umijs.org/docs/max/request#配置
 */
export const request: RequestConfig = {
  baseURL: API_BASE,
  ...errorConfig,
};
