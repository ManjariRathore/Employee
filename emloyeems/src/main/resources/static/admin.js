const API_BASE = "/api/v1/employee";

const state = {
    employees: [],
    editingId: null,
    deletingId: null,
    toastTimer: null
};

const elements = {
    rows: document.querySelector("#employeeRows"),
    emptyState: document.querySelector("#emptyState"),
    emptyMessage: document.querySelector("#emptyMessage"),
    loadingState: document.querySelector("#loadingState"),
    searchInput: document.querySelector("#searchInput"),
    count: document.querySelector("#employeeCount"),
    apiStatus: document.querySelector("#apiStatus"),
    lastRefreshed: document.querySelector("#lastRefreshed"),
    healthDot: document.querySelector("#healthDot"),
    healthLabel: document.querySelector("#healthLabel"),
    employeeModal: document.querySelector("#employeeModal"),
    deleteModal: document.querySelector("#deleteModal"),
    form: document.querySelector("#employeeForm"),
    formMode: document.querySelector("#formMode"),
    formTitle: document.querySelector("#formTitle"),
    id: document.querySelector("#employeeId"),
    name: document.querySelector("#employeeName"),
    address: document.querySelector("#employeeAddress"),
    mobile: document.querySelector("#employeeMobile"),
    saveButton: document.querySelector("#saveButton"),
    deleteMessage: document.querySelector("#deleteMessage"),
    confirmDeleteButton: document.querySelector("#confirmDeleteButton"),
    toast: document.querySelector("#toast"),
    sidebar: document.querySelector(".sidebar"),
    menuButton: document.querySelector("#menuButton")
};

async function apiRequest(path, options = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
            ...(options.body ? {"Content-Type": "application/json"} : {}),
            ...options.headers
        }
    });

    let payload;
    try {
        payload = await response.json();
    } catch (error) {
        throw new Error(`The server returned an invalid response (${response.status}).`);
    }

    if (!response.ok || payload.code !== "00") {
        throw new Error(payload.message || `Request failed (${response.status}).`);
    }

    return payload.content;
}

async function checkHealth() {
    try {
        const response = await fetch("/actuator/health", {cache: "no-store"});
        if (!response.ok) throw new Error("Health endpoint unavailable");
        const health = await response.json();
        const healthy = health.status === "UP";
        elements.healthDot.className = `status-dot ${healthy ? "healthy" : "unhealthy"}`;
        elements.healthLabel.textContent = healthy ? "API operational" : "API degraded";
        elements.apiStatus.textContent = healthy ? "Operational" : "Degraded";
    } catch (error) {
        elements.healthDot.className = "status-dot unhealthy";
        elements.healthLabel.textContent = "API unavailable";
        elements.apiStatus.textContent = "Unavailable";
    }
}

async function loadEmployees({announce = false} = {}) {
    setLoading(true);
    try {
        const employees = await apiRequest("/getAllEmployees");
        state.employees = Array.isArray(employees) ? employees : [];
        elements.count.textContent = state.employees.length.toLocaleString();
        elements.lastRefreshed.textContent = new Intl.DateTimeFormat([], {
            hour: "2-digit",
            minute: "2-digit"
        }).format(new Date());
        renderEmployees();
        if (announce) showToast("Employee directory refreshed.");
    } catch (error) {
        state.employees = [];
        renderEmployees();
        showToast(error.message, true);
    } finally {
        setLoading(false);
    }
}

function renderEmployees() {
    const query = elements.searchInput.value.trim().toLowerCase();
    const filtered = state.employees.filter(employee => [
        employee.empId,
        employee.empName,
        employee.empAddress,
        employee.mnumber ?? employee.mNumber
    ].some(value => String(value ?? "").toLowerCase().includes(query)));

    elements.rows.replaceChildren();

    for (const employee of filtered) {
        const row = document.createElement("tr");
        row.append(
            tableCell(`#${employee.empId}`, "employee-id"),
            employeeNameCell(employee.empName),
            tableCell(employee.empAddress || "—"),
            tableCell(employee.mnumber ?? employee.mNumber ?? "—"),
            actionCell(employee)
        );
        elements.rows.append(row);
    }

    const hasResults = filtered.length > 0;
    elements.emptyState.hidden = hasResults;
    elements.emptyMessage.textContent = query
        ? "Try a different search term."
        : "Add the first employee to get started.";
}

function tableCell(value, className = "") {
    const cell = document.createElement("td");
    cell.textContent = value;
    if (className) cell.className = className;
    return cell;
}

function employeeNameCell(name = "Unnamed employee") {
    const cell = document.createElement("td");
    const wrapper = document.createElement("div");
    wrapper.className = "employee-name";

    const avatar = document.createElement("span");
    avatar.className = "avatar";
    avatar.textContent = initials(name);

    const label = document.createElement("span");
    label.textContent = name;
    wrapper.append(avatar, label);
    cell.append(wrapper);
    return cell;
}

function actionCell(employee) {
    const cell = document.createElement("td");
    cell.className = "row-actions";

    const edit = document.createElement("button");
    edit.className = "action-button";
    edit.type = "button";
    edit.textContent = "Edit";
    edit.addEventListener("click", () => openEmployeeModal(employee));

    const remove = document.createElement("button");
    remove.className = "action-button delete";
    remove.type = "button";
    remove.textContent = "Delete";
    remove.addEventListener("click", () => openDeleteModal(employee));

    cell.append(edit, remove);
    return cell;
}

function initials(name) {
    return String(name || "?")
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map(part => part.charAt(0).toUpperCase())
        .join("") || "?";
}

function openEmployeeModal(employee = null) {
    state.editingId = employee?.empId ?? null;
    elements.form.reset();
    elements.id.value = employee?.empId ?? "";
    elements.name.value = employee?.empName ?? "";
    elements.address.value = employee?.empAddress ?? "";
    elements.mobile.value = employee?.mnumber ?? employee?.mNumber ?? "";
    elements.formMode.textContent = employee ? `Employee #${employee.empId}` : "New record";
    elements.formTitle.textContent = employee ? "Edit employee" : "Add employee";
    elements.saveButton.textContent = employee ? "Save changes" : "Save employee";
    showModal(elements.employeeModal);
    window.setTimeout(() => elements.name.focus(), 50);
}

function closeEmployeeModal() {
    hideModal(elements.employeeModal);
    state.editingId = null;
}

async function saveEmployee(event) {
    event.preventDefault();
    const editing = state.editingId !== null;
    const employee = {
        empId: editing ? state.editingId : 0,
        empName: elements.name.value.trim(),
        empAddress: elements.address.value.trim(),
        mNumber: elements.mobile.value.trim()
    };

    elements.saveButton.disabled = true;
    elements.saveButton.textContent = editing ? "Saving changes…" : "Saving employee…";

    try {
        await apiRequest(editing ? "/updateEmployee" : "/saveEmployee", {
            method: editing ? "PUT" : "POST",
            body: JSON.stringify(employee)
        });
        closeEmployeeModal();
        await loadEmployees();
        showToast(editing ? "Employee updated successfully." : "Employee created successfully.");
    } catch (error) {
        showToast(error.message, true);
    } finally {
        elements.saveButton.disabled = false;
        elements.saveButton.textContent = editing ? "Save changes" : "Save employee";
    }
}

function openDeleteModal(employee) {
    state.deletingId = employee.empId;
    elements.deleteMessage.textContent = `This will permanently remove ${employee.empName || `employee #${employee.empId}`} from the directory.`;
    showModal(elements.deleteModal);
}

function closeDeleteModal() {
    hideModal(elements.deleteModal);
    state.deletingId = null;
}

async function deleteEmployee() {
    if (state.deletingId === null) return;
    const employeeId = state.deletingId;
    elements.confirmDeleteButton.disabled = true;
    elements.confirmDeleteButton.textContent = "Deleting…";

    try {
        await apiRequest(`/deleteEmployee/${employeeId}`, {method: "DELETE"});
        closeDeleteModal();
        await loadEmployees();
        showToast("Employee deleted successfully.");
    } catch (error) {
        showToast(error.message, true);
    } finally {
        elements.confirmDeleteButton.disabled = false;
        elements.confirmDeleteButton.textContent = "Delete employee";
    }
}

function showModal(modal) {
    modal.hidden = false;
    document.body.style.overflow = "hidden";
}

function hideModal(modal) {
    modal.hidden = true;
    document.body.style.overflow = "";
}

function setLoading(loading) {
    elements.loadingState.hidden = !loading;
    if (loading) elements.emptyState.hidden = true;
}

function showToast(message, error = false) {
    window.clearTimeout(state.toastTimer);
    elements.toast.textContent = message;
    elements.toast.className = `toast${error ? " error" : ""}`;
    elements.toast.hidden = false;
    state.toastTimer = window.setTimeout(() => {
        elements.toast.hidden = true;
    }, 3800);
}

function setGreeting() {
    const hour = new Date().getHours();
    document.querySelector("#timeGreeting").textContent = hour < 12 ? "morning" : hour < 18 ? "afternoon" : "evening";
}

document.querySelector("#addEmployeeButton").addEventListener("click", () => openEmployeeModal());
document.querySelector("#closeModalButton").addEventListener("click", closeEmployeeModal);
document.querySelector("#cancelButton").addEventListener("click", closeEmployeeModal);
document.querySelector("#refreshButton").addEventListener("click", () => loadEmployees({announce: true}));
document.querySelector("#cancelDeleteButton").addEventListener("click", closeDeleteModal);
elements.confirmDeleteButton.addEventListener("click", deleteEmployee);
elements.form.addEventListener("submit", saveEmployee);
elements.searchInput.addEventListener("input", renderEmployees);

elements.employeeModal.addEventListener("click", event => {
    if (event.target === elements.employeeModal) closeEmployeeModal();
});
elements.deleteModal.addEventListener("click", event => {
    if (event.target === elements.deleteModal) closeDeleteModal();
});

document.addEventListener("keydown", event => {
    if (event.key !== "Escape") return;
    if (!elements.deleteModal.hidden) closeDeleteModal();
    else if (!elements.employeeModal.hidden) closeEmployeeModal();
});

elements.menuButton.addEventListener("click", () => {
    const open = elements.sidebar.classList.toggle("open");
    elements.menuButton.setAttribute("aria-expanded", String(open));
});
document.querySelectorAll(".nav-link").forEach(link => link.addEventListener("click", () => {
    document.querySelectorAll(".nav-link").forEach(item => item.classList.remove("active"));
    link.classList.add("active");
    elements.sidebar.classList.remove("open");
    elements.menuButton.setAttribute("aria-expanded", "false");
}));

setGreeting();
checkHealth();
loadEmployees();
window.setInterval(checkHealth, 30000);
