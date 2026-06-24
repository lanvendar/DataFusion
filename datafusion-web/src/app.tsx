import { Navigate, Route, Routes } from "react-router-dom";
import { BrowserRouter } from "react-router-dom";
import AppLayout from "@/layout/app-layout";
import MetadataTableStructureDetailPage from "@/modules/metadata-table-structure/detail-page";
import NotFoundPage from "@/modules/not-found";
import SchedulerPluginLogPage from "@/modules/scheduler-instance/plugin-log-page";
import { routeGroups } from "@/router/routes";

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
          <Route path="metadata-table-structure/:id" element={<MetadataTableStructureDetailPage />} />
          <Route path="scheduler-instance/plugin-log" element={<SchedulerPluginLogPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
