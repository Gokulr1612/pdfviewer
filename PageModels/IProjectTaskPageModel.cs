using CommunityToolkit.Mvvm.Input;
using PDFViewer.Models;

namespace PDFViewer.PageModels;

public interface IProjectTaskPageModel
{
	IAsyncRelayCommand<ProjectTask> NavigateToTaskCommand { get; }
	bool IsBusy { get; }
}