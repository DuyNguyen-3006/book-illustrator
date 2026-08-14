import { Navigate, Route, Routes } from "react-router-dom";
import { IdentityPage } from "@/features/auth/pages/IdentityPage";
import { LandingPage } from "@/features/landing/pages/LandingPage";
import { ProjectDetailPage } from "@/features/pipeline/pages/ProjectDetailPage";
import { NewProjectPage } from "@/features/projects/pages/NewProjectPage";
import { ProjectListPage } from "@/features/projects/pages/ProjectListPage";
import { AppLayout } from "./AppLayout";
import { ProtectedRoute } from "./ProtectedRoute";

/** `/` is public; everything under AppLayout sits behind the session guard. */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/signin" element={<IdentityPage />} />
      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/projects" element={<ProjectListPage />} />
        <Route path="/projects/new" element={<NewProjectPage />} />
        <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
