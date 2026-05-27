import { Navigate, Route, Routes } from "react-router-dom";
import { BrowserRouter } from "react-router-dom";
import AppLayout from "@/layout/app-layout";
import { routeGroups } from "@/router/routes";
import NotFoundPage from "@/pages/not-found";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<AppLayout />}>
          <Route index element={<Navigate to="/home" replace />} />
          {routeGroups.flatMap((group) =>
            group.children.map((route) => (
              <Route
                key={route.path}
                path={route.path}
                element={<route.component />}
              />
            )),
          )}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
