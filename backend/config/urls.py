from django.urls import include, path
from rest_framework_simplejwt.views import TokenObtainPairView
from expenses.views import RegisterView

urlpatterns = [
    path("api/auth/login/", TokenObtainPairView.as_view()),
    path("api/auth/register/", RegisterView.as_view()),
    path("api/", include("expenses.urls")),
]
