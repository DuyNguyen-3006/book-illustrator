import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "@/shared/lib/errors";
import { renderWithProviders } from "@/test/renderWithProviders";
import * as projectService from "../services/project.service";
import { NewProjectForm } from "./NewProjectForm";

vi.mock("../services/project.service");

const createProject = vi.mocked(projectService.createProject);

function txtFile(name = "book.txt", contents = "Once upon a time") {
  return new File([contents], name, { type: "text/plain" });
}

describe("NewProjectForm", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("requires a title", async () => {
    renderWithProviders(<NewProjectForm onCreated={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/paste the book/i), "Once upon a time");
    await userEvent.click(screen.getByRole("button", { name: /create project/i }));

    expect(await screen.findByText(/give the project a title/i)).toBeInTheDocument();
    expect(createProject).not.toHaveBeenCalled();
  });

  it("blocks a submit with neither pasted text nor a file", async () => {
    renderWithProviders(<NewProjectForm onCreated={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/title/i), "The Wind in the Willows");
    await userEvent.click(screen.getByRole("button", { name: /create project/i }));

    expect(await screen.findByText(/paste the text or upload a \.txt file/i)).toBeInTheDocument();
    expect(createProject).not.toHaveBeenCalled();
  });

  it("blocks a submit that provides both sources, matching the backend rule", async () => {
    renderWithProviders(<NewProjectForm onCreated={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/title/i), "Both");
    await userEvent.type(screen.getByLabelText(/paste the book/i), "pasted text");
    await userEvent.upload(screen.getByLabelText(/upload a \.txt/i), txtFile());
    await userEvent.click(screen.getByRole("button", { name: /create project/i }));

    expect(await screen.findByText(/use one source, not both/i)).toBeInTheDocument();
    expect(createProject).not.toHaveBeenCalled();
  });

  it("rejects a file that is not a .txt", async () => {
    renderWithProviders(<NewProjectForm onCreated={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/title/i), "Wrong type");
    // applyAccept: false on purpose. The input carries accept=".txt", but the
    // guard under test is what catches a file that arrives anyway (drag and drop,
    // or a browser that ignores the hint).
    await userEvent.upload(
      screen.getByLabelText(/upload a \.txt/i),
      new File(["%PDF"], "book.pdf", { type: "application/pdf" }),
      { applyAccept: false },
    );
    await userEvent.click(screen.getByRole("button", { name: /create project/i }));

    expect(await screen.findByText(/must be a \.txt file/i)).toBeInTheDocument();
    expect(createProject).not.toHaveBeenCalled();
  });

  it("surfaces the backend's rejection and keeps the form usable", async () => {
    createProject.mockRejectedValueOnce(
      new ApiError({ code: "INVALID_INPUT", message: "title must not be blank", retriable: false }, 400),
    );

    renderWithProviders(<NewProjectForm onCreated={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/title/i), "Rejected");
    await userEvent.type(screen.getByLabelText(/paste the book/i), "text");
    await userEvent.click(screen.getByRole("button", { name: /create project/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("title must not be blank");
    expect(screen.getByRole("button", { name: /create project/i })).toBeEnabled();
  });

  it("creates from pasted text and hands back the new project id", async () => {
    createProject.mockResolvedValueOnce({ projectId: 12, title: "The Wind in the Willows" });
    const onCreated = vi.fn();

    renderWithProviders(<NewProjectForm onCreated={onCreated} />);
    await userEvent.type(screen.getByLabelText(/title/i), "  The Wind in the Willows  ");
    await userEvent.type(screen.getByLabelText(/paste the book/i), "Once upon a time");
    await userEvent.click(screen.getByRole("button", { name: /create project/i }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(12));
    expect(createProject.mock.calls[0][0]).toEqual({
      title: "The Wind in the Willows",
      bookText: "Once upon a time",
      file: null,
    });
  });

  it("creates from an uploaded file", async () => {
    createProject.mockResolvedValueOnce({ projectId: 13, title: "Uploaded" });
    const file = txtFile();

    renderWithProviders(<NewProjectForm onCreated={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/title/i), "Uploaded");
    await userEvent.upload(screen.getByLabelText(/upload a \.txt/i), file);
    await userEvent.click(screen.getByRole("button", { name: /create project/i }));

    await waitFor(() => expect(createProject).toHaveBeenCalledTimes(1));
    expect(createProject.mock.calls[0][0]).toEqual({ title: "Uploaded", bookText: "", file });
  });
});
