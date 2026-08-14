import { Navigate, Route, Routes } from "react-router-dom";
import { IdentityPage } from "@/features/auth/pages/IdentityPage";
import { ProjectListPage } from "@/features/projects/pages/ProjectListPage";
import { AppLayout } from "./AppLayout";
import { ProtectedRoute } from "./ProtectedRoute";

/** #22 new project and #23 project detail land here next. */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<IdentityPage />} />
      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/projects" element={<ProjectListPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
