import { create } from 'zustand';

type ViewMode = 'card' | 'table';

interface UiState {
  filterPanelOpen: boolean;
  packageViewMode: ViewMode;
  setFilterPanelOpen: (open: boolean) => void;
  setPackageViewMode: (mode: ViewMode) => void;
}

export const useUiStore = create<UiState>((set) => ({
  filterPanelOpen: false,
  packageViewMode: 'card',
  setFilterPanelOpen: (open) => set({ filterPanelOpen: open }),
  setPackageViewMode: (mode) => set({ packageViewMode: mode }),
}));
